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
import com.vladmihalcea.phd.common.util.PostRange;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.service.ForumService;
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
 * JMH benchmark that measures the execution time of the JPA
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JpaBenchmark {

    private volatile boolean dataSetChanged = true;

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
        if (dataSetChanged) {
            benchmarkSetup.resetTestData();
            dataSetChanged = false;
        }
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void benchmarkLoadDto(ThreadPost post) {
        forumService.loadPostDTO(post.postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingMultiset(ThreadPost post) {
        forumService.loadPostDTOUsingMultiset(post.postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingRest(ThreadPost post) {
        restCaller.loadPostDTO(post.postId);
    }

    @Benchmark
    public void benchmarkLoadDtoUsingBlazeAndRest(ThreadPost post) {
        restCaller.loadPostDTOUsingMultiset(post.postId);
    }

    /**
     * Multi-Ops on JPA (R1-14), which is not idiomatic on JPA: the same six calls as on JDBC and jOOQ, except that
     * the post's own fields are updated with {@code PATCH /post}, since {@code PUT /post} is the JPA Diff-Merge.
     */
    @Benchmark
    public void benchmarkMultiOpsSync(ThreadPost post) {
        dataSetChanged = true;

        post.postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        short version = forumService.patchPost(post.postDTO).getVersion();

        List<PostCommentDto> replies = post.postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        version = forumService.removePostComment(post.postId, version, removed.getId()).getVersion();

        for (PostCommentDto r : replies) {
            r.upvote();
            version = forumService.updatePostComment(post.postId, version, r).getVersion();
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        version = forumService.createPostComment(post.postId, version, newReply).getVersion();

        TagDto jpaTag = post.postDTO.getTags().stream()
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        post.postDTO.getTags().remove(jpaTag);
        version = forumService.removeTag(post.postId, version, jpaTag).getVersion();

        Long tagId = TimeSortedIdentifierGenerator.generate();
        TagDto jakarta = new TagDto()
            .setId(tagId)
            .setName("Jakarta Persistence " + tagId);
        post.postDTO.getTags().add(jakarta);
        version = forumService.createTag(post.postId, version, jakarta).getVersion();
    }

    @Benchmark
    public void benchmarkMultiOpsSyncUsingRest(ThreadPost post) {
        dataSetChanged = true;

        post.postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        short version = restCaller.patchPost(post.postDTO);

        List<PostCommentDto> replies = post.postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        version = restCaller.removePostComment(post.postId, version, removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            version = restCaller.updatePostComment(post.postId, version, r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        version = restCaller.createPostComment(post.postId, version, newReply);

        TagDto jpaTag = post.postDTO.getTags().stream()
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        post.postDTO.getTags().remove(jpaTag);
        version = restCaller.removeTag(post.postId, version, jpaTag);

        Long tagId = TimeSortedIdentifierGenerator.generate();
        TagDto jakarta = new TagDto()
            .setId(tagId)
            .setName("Jakarta Persistence " + tagId);
        post.postDTO.getTags().add(jakarta);
        version = restCaller.createTag(post.postId, version, jakarta);
    }

    @Benchmark
    public void benchmarkDiffMergeSync(ThreadPost post) {
        dataSetChanged = true;

        post.postDTO.setTitle("DTO to Entity Auto-Sync Pattern").upvote();

        List<PostCommentDto> replies = post.postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);
        replies.add(
            new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        post.postDTO.getTags().remove(
            post.postDTO.getTags().stream()
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow()
        );
        Long tagId = TimeSortedIdentifierGenerator.generate();
        post.postDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        forumService.updatePost(post.postDTO);
    }

    @Benchmark
    public void benchmarkDiffMergeSyncUsingRest(ThreadPost post) {
        dataSetChanged = true;

        post.postDTO.setTitle("DTO to Entity Auto-Sync Pattern").upvote();

        List<PostCommentDto> replies = post.postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);
        replies.add(
            new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        post.postDTO.getTags().remove(
            post.postDTO.getTags().stream()
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow()
        );
        Long tagId = TimeSortedIdentifierGenerator.generate();
        post.postDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        restCaller.updatePost(post.postDTO);
    }

    /**
     * The post that the current benchmark thread reads or edits next, from a range of posts that no other thread uses.
     */
    @State(Scope.Thread)
    public static class ThreadPost {

        private PostRange postRange;

        private Long postId;
        private PostDto postDTO;

        @Setup(Level.Trial)
        public void assignRange(ThreadParams threadParams, JpaBenchmark benchmark) {
            postRange = new PostRange(threadParams, benchmark.benchmarkSetup.generator().editablePostCount());
        }

        @Setup(Level.Iteration)
        public void onEachIteration() {
            postRange.reset();
        }

        @Setup(Level.Invocation)
        public void onEachInvocation(JpaBenchmark benchmark) {
            try (LayerTimings.Pause pause = LayerTimings.pause()) {
                // Only the write benchmarks change the data set, and they must not edit a post twice
                postId = postRange.nextPostId(!benchmark.dataSetChanged);
                postDTO = benchmark.forumService.loadPostDTO(postId);
                UserContext.setCurrentUser(postDTO.getAuthor());
            }
        }
    }
}
