package com.vladmihalcea.phd.jdbc.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
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
public class JdbcVariableBenchmark {

    private ConfigurableApplicationContext context;

    private RestCaller restCaller;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JdbcConfiguration.class,
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
    public void benchmarkLoadDtoUsingRest_1_collection_size() {
        restCaller.loadPostDTO(100_001L);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest_5_collection_size() {
        restCaller.loadPostDTO(100_005L);
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
    public void benchmarkLoadJsonStringRest_0_collection_size() {
        restCaller.loadPostJsonString(100_000L);
    }

    @Benchmark
    public void benchmarkLoadJsonStringRest_1_collection_size() {
        restCaller.loadPostJsonString(100_001L);
    }

    @Benchmark
    public void benchmarkLoadJsonStringRest_5_collection_size() {
        restCaller.loadPostJsonString(100_005L);
    }

    @Benchmark
    public void benchmarkLoadJsonStringRest_25_collection_size() {
        restCaller.loadPostJsonString(100_025L);
    }

    @Benchmark
    public void benchmarkLoadJsonStringRest_100_collection_size() {
        restCaller.loadPostJsonString(100_100L);
    }
}

