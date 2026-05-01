package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.service.ForumService;
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
 * JMH benchmark that measures the execution time of the JPA
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JpaBenchmark {

    private boolean dataSetChanged = true;

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private BenchmarkTestData benchmarkSetup;

    private Long postId;

    private PostDto postDTO;

    private RestCaller restCaller;
    private AtomicLong postIdGenerator = new AtomicLong();
    private int POST_COUNT = 20_000;

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

        forumService = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(BenchmarkTestData.class);

        int port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

        restCaller = new RestCaller(
            port, new RestTemplate()
        );
    }

    @TearDown(Level.Trial)
    public void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * Recreates the standard fixture before every benchmark invocation.
     */
    @Setup(Level.Iteration)
    public void onEachIteration() {
        resetData();
    }

    @Setup(Level.Invocation)
    public void onEachInvocation() {
        postId = (postIdGenerator.getAndIncrement() % POST_COUNT) + 1;
        postDTO = forumService.loadPostDTO(postId);
        UserContext.setCurrentUser(postDTO.getAuthor());
    }

    private void resetData() {
        if (dataSetChanged) {
            benchmarkSetup.deleteAll();

            benchmarkSetup.createTestData();
            dataSetChanged = false;
            postIdGenerator.set(0);
        }
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void benchmarkLoadDto() {
        forumService.loadPostDTO(postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingMultiset() {
        forumService.loadPostDTOUsingMultiset(postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest() {
        restCaller.loadPostDTO(postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest() {
        restCaller.loadPostDTOUsingMultiset(postId);
    }

    @Benchmark
    public void benchmarkDiffMergeSync() {
        dataSetChanged = true;

        postDTO.setTitle("DTO to Entity Auto-Sync Pattern").upvote();

        List<PostCommentDto> replies = postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);
        replies.add(
            new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        postDTO.getTags().remove(
            postDTO.getTags().stream()
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow()
        );
        Long tagId = TimeSortedIdentifierGenerator.generate();
        postDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        forumService.updatePost(postDTO);
    }

    @Benchmark
    public void benchmarkDiffMergeSyncUsingRest() {
        dataSetChanged = true;

        postDTO.setTitle("DTO to Entity Auto-Sync Pattern").upvote();

        List<PostCommentDto> replies = postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);
        replies.add(
            new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        postDTO.getTags().remove(
            postDTO.getTags().stream()
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow()
        );
        Long tagId = TimeSortedIdentifierGenerator.generate();
        postDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        restCaller.updatePost(postDTO);
    }
}

