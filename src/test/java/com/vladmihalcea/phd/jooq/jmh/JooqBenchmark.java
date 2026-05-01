package com.vladmihalcea.phd.jooq.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.jooq.service.JooqForumService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

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
public class JooqBenchmark {

    private ConfigurableApplicationContext context;
    private JooqForumService forumService;
    private JooqBenchmarkSetup benchmarkSetup;

    private Long postId;

    private PostDto postDTO;
    private PostDto modifiedPostDTO;

    private RestCaller restCaller;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JooqConfiguration.class,
            SpringWebConfiguration.class,
            ForumApplication.class
        );
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setDefaultProperties(Map.of("server.port", "0"));
        context = app.run();

        forumService = context.getBean(JooqForumService.class);
        benchmarkSetup = context.getBean(JooqBenchmarkSetup.class);

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
    @Setup(Level.Invocation)
    public void resetData() {
        benchmarkSetup.deleteAll();
        postId = benchmarkSetup.createTestData();
        postDTO = forumService.loadPostDTO(postId);
        modifiedPostDTO = JsonUtil.clone(postDTO);
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
        forumService.loadPostDTO(postId);
    }

    @Benchmark
    public void benchmarkLoadJsonNodeUsingMultiset() {
        forumService.loadPostJsonNodeUsingMultiset(postId);
    }

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

    @Benchmark
    public void benchmarkMultiOpsSync() {
        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        forumService.updatePost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        forumService.removePostComment(removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            forumService.updatePostComment(r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        forumService.createPostComment(postId, newReply);

        TagDto jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        forumService.removeTag(postId, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        forumService.createTag(postId, jakarta);
    }

    @Benchmark
    public void benchmarkMultiOpsSyncUsingRest() {
        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        restCaller.updatePost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        restCaller.removePostComment(removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            restCaller.updatePostComment(r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        restCaller.createPostComment(postId, newReply);

        TagDto jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        restCaller.removeTag(postId, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        restCaller.createTag(postId, jakarta);
    }

    @Benchmark
    public void benchmarkDiffMergeSync() {
        modifiedPostDTO.setTitle("Diff Merge DTO Sync Pattern").upvote();

        List<PostCommentDto> replies = modifiedPostDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDto jpaTag = modifiedPostDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPostDTO.getTags().remove(jpaTag);
        modifiedPostDTO.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        forumService.diffMerge(postDTO, modifiedPostDTO);
    }

    @Benchmark
    public void benchmarkDiffMergeSyncUsingRest() {
        modifiedPostDTO.setTitle("Diff Merge DTO Sync Pattern").upvote();

        List<PostCommentDto> replies = modifiedPostDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDto jpaTag = modifiedPostDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPostDTO.getTags().remove(jpaTag);
        modifiedPostDTO.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        restCaller.diffMerge(postDTO, modifiedPostDTO);
    }
}

