package com.vladmihalcea.phd.jdbc.jmh;

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
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
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
 * JMH benchmark that measures the execution time of the JDBC
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JdbcUpdateBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private BenchmarkTestData benchmarkSetup;

    private Long postId;

    private PostDto postDTO;
    private PostDto modifiedPostDTO;

    private RestCaller restCaller;
    private AtomicLong postIdGenerator = new AtomicLong();
    private int POST_COUNT = 20_000;

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
        modifiedPostDTO = JsonUtil.clone(postDTO);
    }

    private void resetData() {
        benchmarkSetup.createTestData();
        postIdGenerator.set(0);
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

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
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        restCaller.removeTag(postId, jpaTag);

        Long tagId = TimeSortedIdentifierGenerator.generate();
        TagDto jakarta = new TagDto()
            .setId(tagId)
            .setName("Jakarta Persistence " + tagId);
        postDTO.getTags().add(jakarta);
        restCaller.createTag(postId, jakarta);
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
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPostDTO.getTags().remove(jpaTag);
        Long tagId = TimeSortedIdentifierGenerator.generate();
        modifiedPostDTO.getTags().add(
            new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId)
        );

        restCaller.diffMerge(postDTO, modifiedPostDTO);
    }
}

