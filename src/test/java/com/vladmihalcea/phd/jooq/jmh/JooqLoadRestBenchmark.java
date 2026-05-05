package com.vladmihalcea.phd.jooq.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JMH benchmark that measures the execution time of the jOOQ
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JooqLoadRestBenchmark {

    private ConfigurableApplicationContext context;

    private RestCaller restCaller;

    private Long postId;
    private AtomicLong postIdGenerator = new AtomicLong();
    private int POST_COUNT = 20_000;

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

    @Setup(Level.Invocation)
    public void onEachInvocation() {
        postId = (postIdGenerator.getAndIncrement() % POST_COUNT) + 1;
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void benchmarkLoadDtoUsingRest() {
        restCaller.loadPostDTO(postId);
    }

    @Benchmark
    public void benchmarkLoadJsonNodeUsingMultisetAndRest() {
        restCaller.loadPostJsonNode(postId);
    }

    @Benchmark
    public void benchmarkLoadJsonStringUsingMultisetAndRest() {
        restCaller.loadPostJsonString(postId);
    }
}

