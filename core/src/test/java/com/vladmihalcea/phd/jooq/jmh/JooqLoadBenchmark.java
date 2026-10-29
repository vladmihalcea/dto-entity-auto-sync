package com.vladmihalcea.phd.jooq.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.common.util.BenchmarkRestTemplate;
import com.vladmihalcea.phd.common.timing.LayerTimings;
import com.vladmihalcea.phd.common.dto.PostDto;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.vladmihalcea.phd.common.config.DataSetGenerator.FAN_OUT_POST_ID;

/**
 * JMH benchmark that measures the execution time of the JPA
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JooqLoadBenchmark {

    private ConfigurableApplicationContext context;

    private RestCaller restCaller;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JooqConfiguration.class,
            BenchmarkTestData.class,
            SpringRestConfiguration.class,
            ForumApplication.class
        );
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setDefaultProperties(Map.of("server.port", "0"));
        context = app.run();

        int port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

        restCaller = new RestCaller(
            port, LayerTimings.timed(BenchmarkRestTemplate.create())
        );

        BenchmarkTestData benchmarkSetup = context.getBean(BenchmarkTestData.class);
        if(!benchmarkSetup.isDataValid()) {
            benchmarkSetup.createTestData();
        }
    }

    @TearDown(Level.Trial)
    public void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public PostDto benchmarkLoadDtoUsingRest_0_collection_size() {
        return restCaller.loadPostDTO(FAN_OUT_POST_ID + 0);
    }

    @Benchmark
    public PostDto benchmarkLoadDtoUsingRest_5_collection_size() {
        return restCaller.loadPostDTO(FAN_OUT_POST_ID + 5);
    }

    @Benchmark
    public PostDto benchmarkLoadDtoUsingRest_10_collection_size() {
        return restCaller.loadPostDTO(FAN_OUT_POST_ID + 10);
    }

    @Benchmark
    public PostDto benchmarkLoadDtoUsingRest_25_collection_size() {
        return restCaller.loadPostDTO(FAN_OUT_POST_ID + 25);
    }

    @Benchmark
    public PostDto benchmarkLoadDtoUsingRest_100_collection_size() {
        return restCaller.loadPostDTO(FAN_OUT_POST_ID + 100);
    }

    @Benchmark
    public String benchmarkLoadJsonStringRest_0_collection_size() {
        return restCaller.loadPostJsonString(FAN_OUT_POST_ID + 0);
    }

    @Benchmark
    public String benchmarkLoadJsonStringRest_5_collection_size() {
        return restCaller.loadPostJsonString(FAN_OUT_POST_ID + 5);
    }

    @Benchmark
    public String benchmarkLoadJsonStringRest_10_collection_size() {
        return restCaller.loadPostJsonString(FAN_OUT_POST_ID + 10);
    }

    @Benchmark
    public String benchmarkLoadJsonStringRest_25_collection_size() {
        return restCaller.loadPostJsonString(FAN_OUT_POST_ID + 25);
    }

    @Benchmark
    public String benchmarkLoadJsonStringRest_100_collection_size() {
        return restCaller.loadPostJsonString(FAN_OUT_POST_ID + 100);
    }

    @Benchmark
    public String benchmarkLoadDtoUsingMultisetAndRest_0_collection_size() {
        return restCaller.loadPostDTOUsingMultisetAsString(FAN_OUT_POST_ID + 0);
    }

    @Benchmark
    public String benchmarkLoadDtoUsingMultisetAndRest_10_collection_size() {
        return restCaller.loadPostDTOUsingMultisetAsString(FAN_OUT_POST_ID + 10);
    }

    @Benchmark
    public String benchmarkLoadDtoUsingMultisetAndRest_100_collection_size() {
        return restCaller.loadPostDTOUsingMultisetAsString(FAN_OUT_POST_ID + 100);
    }
}

