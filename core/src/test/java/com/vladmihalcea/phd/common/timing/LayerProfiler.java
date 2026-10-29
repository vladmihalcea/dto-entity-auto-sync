package com.vladmihalcea.phd.common.timing;

import com.vladmihalcea.phd.common.timing.LayerTimings.Layer;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.AggregationPolicy;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.ScalarResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * JMH profiler that reports, as secondary results of every iteration, the time that an operation spends
 * in every layer, e.g., {@code layer.http.client.ms} and {@code layer.sql.ms} per operation, the number of
 * HTTP calls, SQL statements, and commits per operation, and the client and server time of an HTTP call
 * per HTTP method, e.g., {@code layer.http.post.client.ms} per call. It divides the totals by all the
 * operations of the iteration, including the ones that JMH runs outside the measurement window
 * to synchronize the threads, since the probes record those as well.
 * <p>
 * It also reports {@code layer.throughput}, the operations of all threads per second of the measurement window.
 * Under concurrency, the threads divided by the mean response time overstate it whenever a benchmark prepares
 * every invocation in an untimed fixture, e.g., the write benchmarks, which load the next post before every edit.
 * <p>
 * Usage: {@code -prof com.vladmihalcea.phd.common.timing.LayerProfiler}
 *
 * @author Vlad Mihalcea
 */
public class LayerProfiler implements InternalProfiler {

    @Override
    public String getDescription() {
        return "Time per operation in the HTTP client, the HTTP server, the service, the SQL statements, and the commits";
    }

    @Override
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        LayerTimings.startRecording();
    }

    @Override
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        LayerTimings.stopRecording();
        long operations = result.getMetadata().getAllOps();
        List<Result> results = new ArrayList<>();
        if (operations == 0) {
            return results;
        }
        for (Layer layer : Layer.values()) {
            long calls = LayerTimings.LAYER_CALLS[layer.ordinal()].sum();
            if (calls > 0) {
                results.add(average("layer." + layer.label() + ".ms", millis(LayerTimings.LAYER_NANOS[layer.ordinal()]) / operations, "ms/op"));
            }
        }
        double seconds = iterationParams.getTime().convertTo(TimeUnit.NANOSECONDS) / 1e9;
        if (seconds > 0) {
            results.add(average("layer.throughput", result.getMetadata().getMeasuredOps() / seconds, "ops/s"));
        }
        addCount(results, "layer.http.calls", Layer.HTTP_CLIENT, operations);
        addCount(results, "layer.sql.statements", Layer.SQL, operations);
        addCount(results, "layer.commits", Layer.COMMIT, operations);
        for (int i = 0; i < LayerTimings.HTTP_METHODS.size(); i++) {
            String method = LayerTimings.HTTP_METHODS.get(i).toLowerCase();
            addPerCall(results, "layer.http." + method + ".client.ms", LayerTimings.CLIENT_METHOD_NANOS[i], LayerTimings.CLIENT_METHOD_CALLS[i]);
            addPerCall(results, "layer.http." + method + ".server.ms", LayerTimings.SERVER_METHOD_NANOS[i], LayerTimings.SERVER_METHOD_CALLS[i]);
        }
        return results;
    }

    private static void addCount(List<Result> results, String label, Layer layer, long operations) {
        long calls = LayerTimings.LAYER_CALLS[layer.ordinal()].sum();
        if (calls > 0) {
            results.add(average(label, (double) calls / operations, "#/op"));
        }
    }

    private static void addPerCall(List<Result> results, String label, LongAdder nanos, LongAdder calls) {
        long callCount = calls.sum();
        if (callCount > 0) {
            results.add(average(label, millis(nanos) / callCount, "ms/call"));
        }
    }

    private static double millis(LongAdder nanos) {
        return nanos.sum() / 1_000_000.0;
    }

    private static ScalarResult average(String label, double value, String unit) {
        return new ScalarResult(label, value, unit, AggregationPolicy.AVG);
    }
}
