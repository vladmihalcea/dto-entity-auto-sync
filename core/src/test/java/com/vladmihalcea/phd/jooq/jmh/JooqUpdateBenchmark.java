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
import com.vladmihalcea.phd.common.util.PostRange;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.common.util.BenchmarkRestTemplate;
import com.vladmihalcea.phd.common.timing.LayerTimings;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.ThreadParams;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures the execution time of the jOOQ
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JooqUpdateBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private BenchmarkTestData benchmarkSetup;

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

        forumService = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(BenchmarkTestData.class);

        int port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);

        restCaller = new RestCaller(
            port, LayerTimings.timed(BenchmarkRestTemplate.create())
        );
    }

    @TearDown(Level.Trial)
    public void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    /**
     * Prepares the data set for the next iteration: the first iteration of a fork loads it,
     * and every later one restores only the posts that the previous iteration edited.
     */
    @Setup(Level.Iteration)
    public void onEachIteration() {
        try (LayerTimings.Pause pause = LayerTimings.pause()) {
            resetData();
        }
    }

    private void resetData() {
        benchmarkSetup.resetTestData();
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void benchmarkMultiOpsSyncUsingRest(ThreadPost post) {
        post.postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        restCaller.updatePost(post.postDTO);

        List<PostCommentDto> replies = post.postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        restCaller.removePostComment(post.postId, removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            restCaller.updatePostComment(post.postId, r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        restCaller.createPostComment(post.postId, newReply);

        TagDto jpaTag = post.postDTO.getTags().stream()
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        post.postDTO.getTags().remove(jpaTag);
        restCaller.removeTag(post.postId, jpaTag);

        Long tagId = TimeSortedIdentifierGenerator.generate();
        TagDto jakarta = new TagDto()
            .setId(tagId)
            .setName("Jakarta Persistence " + tagId);
        post.postDTO.getTags().add(jakarta);
        restCaller.createTag(post.postId, jakarta);
    }

    @Benchmark
    public void benchmarkDiffMergeSyncUsingRest(ThreadPost post) {
        post.modifiedPostDTO.setTitle("Diff Merge DTO Sync Pattern").upvote();

        List<PostCommentDto> replies = post.modifiedPostDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDto jpaTag = post.modifiedPostDTO.getTags().stream()
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        post.modifiedPostDTO.getTags().remove(jpaTag);
        Long tagId = TimeSortedIdentifierGenerator.generate();
        post.modifiedPostDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        restCaller.diffMerge(post.postDTO, post.modifiedPostDTO);
    }

    /**
     * The post that the current benchmark thread edits next, from a range of posts that no other thread uses.
     */
    @State(Scope.Thread)
    public static class ThreadPost {

        private PostRange postRange;

        private Long postId;
        private PostDto postDTO;
        private PostDto modifiedPostDTO;

        @Setup(Level.Trial)
        public void assignRange(ThreadParams threadParams, JooqUpdateBenchmark benchmark) {
            postRange = new PostRange(threadParams, benchmark.benchmarkSetup.generator().editablePostCount());
        }

        @Setup(Level.Iteration)
        public void onEachIteration() {
            postRange.reset();
        }

        @Setup(Level.Invocation)
        public void onEachInvocation(JooqUpdateBenchmark benchmark) {
            try (LayerTimings.Pause pause = LayerTimings.pause()) {
                postId = postRange.nextPostId(false);
                postDTO = benchmark.forumService.loadPostDTO(postId);
                UserContext.setCurrentUser(postDTO.getAuthor());
                modifiedPostDTO = JsonUtil.clone(postDTO);
            }
        }
    }
}
