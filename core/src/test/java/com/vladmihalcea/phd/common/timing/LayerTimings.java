package com.vladmihalcea.phd.common.timing;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionContext;
import net.ttddyy.dsproxy.listener.MethodExecutionListener;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Accumulates the time that the benchmark operations spend in every layer of the application: the HTTP client,
 * the HTTP server, the service, the SQL statements, and the commits. Since the client and the server run in the same JVM,
 * the client time minus the server time is the time of the HTTP transport.
 * <p>
 * The probes record only while the {@link LayerProfiler} measures an iteration, and not while a benchmark fixture
 * pauses them, e.g., to load the post of the next invocation. Otherwise, a probe costs one volatile read.
 * The probes aggregate in memory instead of logging every call, since logging would add I/O to the measured operations.
 *
 * @author Vlad Mihalcea
 */
public final class LayerTimings {

    public enum Layer {
        HTTP_CLIENT("http.client"),
        HTTP_SERVER("http.server"),
        SERVICE("service"),
        SQL("sql"),
        COMMIT("commit");

        private final String label;

        Layer(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * The HTTP methods whose calls are also timed separately, since their transport cost can differ.
     */
    static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

    private static final int LAYERS = Layer.values().length;

    static final LongAdder[] LAYER_NANOS = adders(LAYERS);
    static final LongAdder[] LAYER_CALLS = adders(LAYERS);
    static final LongAdder[] CLIENT_METHOD_NANOS = adders(HTTP_METHODS.size());
    static final LongAdder[] CLIENT_METHOD_CALLS = adders(HTTP_METHODS.size());
    static final LongAdder[] SERVER_METHOD_NANOS = adders(HTTP_METHODS.size());
    static final LongAdder[] SERVER_METHOD_CALLS = adders(HTTP_METHODS.size());

    private static final ThreadLocal<int[]> PAUSES = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<long[]> QUERY_BEGIN = ThreadLocal.withInitial(() -> new long[1]);
    private static final ThreadLocal<long[]> COMMIT_BEGIN = ThreadLocal.withInitial(() -> new long[1]);

    private static final Pause PAUSE = () -> PAUSES.get()[0]--;

    private static volatile boolean recording;

    private LayerTimings() {
    }

    /**
     * Ends a pause of the probes of the current thread.
     */
    public interface Pause extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Pauses the probes of the current thread until the returned {@link Pause} closes,
     * so the work of a benchmark fixture does not count as part of the measured operations.
     */
    public static Pause pause() {
        PAUSES.get()[0]++;
        return PAUSE;
    }

    static void startRecording() {
        for (LongAdder[] adders : List.of(LAYER_NANOS, LAYER_CALLS, CLIENT_METHOD_NANOS, CLIENT_METHOD_CALLS, SERVER_METHOD_NANOS, SERVER_METHOD_CALLS)) {
            Arrays.stream(adders).forEach(LongAdder::reset);
        }
        recording = true;
    }

    static void stopRecording() {
        recording = false;
    }

    /**
     * @return the start time of the probed call, or 0 if the probe does not record
     */
    static long begin() {
        return recording && PAUSES.get()[0] == 0 ? System.nanoTime() : 0;
    }

    static void end(Layer layer, long begin) {
        if (begin != 0) {
            LAYER_NANOS[layer.ordinal()].add(System.nanoTime() - begin);
            LAYER_CALLS[layer.ordinal()].increment();
        }
    }

    static void end(Layer layer, String httpMethod, long begin) {
        if (begin != 0) {
            long nanos = System.nanoTime() - begin;
            LAYER_NANOS[layer.ordinal()].add(nanos);
            LAYER_CALLS[layer.ordinal()].increment();
            int method = HTTP_METHODS.indexOf(httpMethod);
            if (method >= 0) {
                (layer == Layer.HTTP_CLIENT ? CLIENT_METHOD_NANOS : SERVER_METHOD_NANOS)[method].add(nanos);
                (layer == Layer.HTTP_CLIENT ? CLIENT_METHOD_CALLS : SERVER_METHOD_CALLS)[method].increment();
            }
        }
    }

    /**
     * Times every call of the client from the creation of the request to the closing of the response, which follows
     * the reading of the response body. It wraps the request factory instead of adding an interceptor, since a
     * {@code RestTemplate} with interceptors buffers the request bodies, which would change the HTTP client behavior.
     */
    public static RestTemplate timed(RestTemplate restTemplate) {
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        restTemplate.setRequestFactory((uri, method) -> new TimedRequest(requestFactory.createRequest(uri, method), begin()));
        return restTemplate;
    }

    /**
     * Times the execution of every SQL statement, a batch counting as one statement, i.e., one database round trip.
     */
    public static QueryExecutionListener queryListener() {
        return new QueryExecutionListener() {
            @Override
            public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
                QUERY_BEGIN.get()[0] = begin();
            }

            @Override
            public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
                end(Layer.SQL, QUERY_BEGIN.get()[0]);
            }
        };
    }

    /**
     * Times every commit of a JDBC connection.
     */
    public static MethodExecutionListener commitListener() {
        return new MethodExecutionListener() {
            @Override
            public void beforeMethod(MethodExecutionContext executionContext) {
                if (executionContext.getMethod().getName().equals("commit")) {
                    COMMIT_BEGIN.get()[0] = begin();
                }
            }

            @Override
            public void afterMethod(MethodExecutionContext executionContext) {
                if (executionContext.getMethod().getName().equals("commit")) {
                    end(Layer.COMMIT, COMMIT_BEGIN.get()[0]);
                }
            }
        };
    }

    private static LongAdder[] adders(int size) {
        LongAdder[] adders = new LongAdder[size];
        Arrays.setAll(adders, i -> new LongAdder());
        return adders;
    }

    /**
     * Implements {@link StreamingHttpOutputMessage}, like the requests of the default {@code SimpleClientHttpRequestFactory},
     * since the message converters stream the body only into a streaming request and buffer it otherwise.
     */
    private record TimedRequest(ClientHttpRequest request, long begin) implements ClientHttpRequest, StreamingHttpOutputMessage {

        @Override
        public void setBody(Body body) {
            if (request instanceof StreamingHttpOutputMessage streamingRequest) {
                streamingRequest.setBody(body);
            } else {
                try {
                    body.writeTo(request.getBody());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }

        @Override
        public void setBody(byte[] body) throws IOException {
            if (request instanceof StreamingHttpOutputMessage streamingRequest) {
                streamingRequest.setBody(body);
            } else {
                request.getBody().write(body);
            }
        }

        @Override
        public ClientHttpResponse execute() throws IOException {
            return new TimedResponse(request.execute(), request.getMethod().name(), begin);
        }

        @Override
        public HttpMethod getMethod() {
            return request.getMethod();
        }

        @Override
        public URI getURI() {
            return request.getURI();
        }

        @Override
        public Map<String, Object> getAttributes() {
            return request.getAttributes();
        }

        @Override
        public HttpHeaders getHeaders() {
            return request.getHeaders();
        }

        @Override
        public OutputStream getBody() throws IOException {
            return request.getBody();
        }
    }

    private static final class TimedResponse implements ClientHttpResponse {

        private final ClientHttpResponse response;
        private final String httpMethod;
        private final long begin;
        private boolean closed;

        private TimedResponse(ClientHttpResponse response, String httpMethod, long begin) {
            this.response = response;
            this.httpMethod = httpMethod;
            this.begin = begin;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return response.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return response.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return response.getHeaders();
        }

        @Override
        public InputStream getBody() throws IOException {
            return response.getBody();
        }

        @Override
        public void close() {
            response.close();
            if (!closed) {
                closed = true;
                end(Layer.HTTP_CLIENT, httpMethod, begin);
            }
        }
    }
}
