package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.common.util.BenchmarkRestTemplate;
import com.vladmihalcea.phd.common.timing.LayerTimings;
import com.vladmihalcea.phd.common.dto.PostDto;
import tools.jackson.databind.JsonNode;
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
public class JpaLoadBenchmark {

    private ConfigurableApplicationContext context;

    private RestCaller restCaller;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JpaConfiguration.class,
            BenchmarkTestData.class,
            SpringWebConfiguration.class,
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
    public JsonNode benchmarkLoadDtoUsingBlazeAndRest_0_collection_size() {
        return restCaller.loadPostDTOUsingMultiset(FAN_OUT_POST_ID + 0);
    }

    @Benchmark
    public JsonNode benchmarkLoadDtoUsingBlazeAndRest_5_collection_size() {
        return restCaller.loadPostDTOUsingMultiset(FAN_OUT_POST_ID + 5);
    }

    @Benchmark
    public JsonNode benchmarkLoadDtoUsingBlazeAndRest_10_collection_size() {
        return restCaller.loadPostDTOUsingMultiset(FAN_OUT_POST_ID + 10);
    }

    @Benchmark
    public JsonNode benchmarkLoadDtoUsingBlazeAndRest_25_collection_size() {
        return restCaller.loadPostDTOUsingMultiset(FAN_OUT_POST_ID + 25);
    }

    @Benchmark
    public JsonNode benchmarkLoadDtoUsingBlazeAndRest_100_collection_size() {
        return restCaller.loadPostDTOUsingMultiset(FAN_OUT_POST_ID + 100);
    }
}

