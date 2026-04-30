package com.vladmihalcea.phd.jdbc.jmh;

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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures the execution time of the JDBC
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JdbcBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private JdbcBenchmarkSetup benchmarkSetup;

    private Long postId;

    private PostDto postDTO;
    private PostDto modifiedPostDTO;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JdbcConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        context = app.run();

        forumService = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JdbcBenchmarkSetup.class);
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
}

