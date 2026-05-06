package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures the execution time of the JPA
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JpaVariableBenchmark {

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
            port, new RestTemplate()
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
    public void benchmarkLoadDtoUsingRest_0_collection_size() {
        restCaller.loadPostDTO(100_000L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest_5_collection_size() {
        restCaller.loadPostDTO(100_005L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest_10_collection_size() {
        restCaller.loadPostDTO(100_010L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest_25_collection_size() {
        restCaller.loadPostDTO(100_025L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest_100_collection_size() {
        restCaller.loadPostDTO(100_100L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest_0_collection_size() {
        restCaller.loadPostDTOUsingMultiset(100_000L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest_5_collection_size() {
        restCaller.loadPostDTOUsingMultiset(100_005L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest_10_collection_size() {
        restCaller.loadPostDTOUsingMultiset(100_010L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest_25_collection_size() {
        restCaller.loadPostDTOUsingMultiset(100_025L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest_100_collection_size() {
        restCaller.loadPostDTOUsingMultiset(100_100L);
    }
}

