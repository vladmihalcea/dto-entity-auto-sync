package com.vladmihalcea.phd.common.util;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;

/**
 * Creates the {@code RestTemplate} of the REST benchmarks, which uses the JDK {@code HttpClient} over HTTP/1.1.
 * <p>
 * The default client of {@code RestTemplate}, which uses {@code HttpURLConnection}, checks a pooled connection
 * before every streaming {@code POST} with a read whose socket timeout is 1 ms ({@code sun.net.www.http.HttpClient.available()}),
 * and Windows rounds that timeout up to its 15.6 ms timer tick. That added about 9 ms to every {@code POST},
 * but not to the other HTTP methods, so it distorted every comparison between write patterns that use different methods.
 * The {@code benchmark.http.client=simple} system property selects that client, to reproduce the original measurements.
 *
 * @author Vlad Mihalcea
 */
public final class BenchmarkRestTemplate {

    private BenchmarkRestTemplate() {
    }

    public static RestTemplate create() {
        if ("simple".equals(System.getProperty("benchmark.http.client"))) {
            return new RestTemplate();
        }
        return new RestTemplate(new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        ));
    }
}
