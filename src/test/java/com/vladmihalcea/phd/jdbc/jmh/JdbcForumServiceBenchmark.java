package com.vladmihalcea.phd.jdbc.jmh;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcDTOManualSyncConfiguration;
import com.vladmihalcea.phd.jdbc.service.ForumService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JMH benchmark that measures the execution time of the JDBC
 * {@link ForumService} for the two main test scenarios defined in
 * {@code JdbcDTOManualSyncTest}:
 * <ul>
 *   <li>{@link #benchmarkMultiOpsDtoSync()} ()} — mirrors {@code testMultiOpsDtoSync}</li>
 *   <li>{@link #benchmarkRemoveParentPostComment()} — mirrors {@code testRemoveParentPostComment}</li>
 * </ul>
 *
 * <p>The Spring context (JDBC + TestContainers, no web server) is started once
 * per trial and the test dataset is rebuilt before every invocation so each
 * call always starts from the same known state.
 *
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JdbcForumServiceBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private JdbcForumBenchmarkSetup benchmarkSetup;

    private Long postId;

    private PostDTO postDTO;
    private PostDTO modifiedPostDTO;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JdbcDTOManualSyncConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        context = app.run();

        forumService = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JdbcForumBenchmarkSetup.class);
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
    public void benchmarkMultiOpsDtoSync() {
        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        forumService.updatePost(postDTO);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        forumService.removePostComment(removed.getId());

        for (PostCommentDTO r : replies) {
            r.upvote();
            forumService.updatePostComment(r);
        }

        PostCommentDTO newReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        forumService.createPostComment(postId, newReply);

        TagDTO jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        forumService.removeTag(postId, jpaTag);

        TagDTO jakarta = new TagDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        forumService.createTag(postId, jakarta);
    }

    @Benchmark
    public void benchmarkRemoveParentPostComment() {
        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        forumService.removePostComment(parent.getId());
    }

    @Benchmark
    public void benchmarkDiffMergeSync() {
        modifiedPostDTO.setTitle("Diff Merge DTO Sync Pattern").upvote();

        List<PostCommentDTO> replies = modifiedPostDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDTO::upvote);

        PostCommentDTO newReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDTO jpaTag = modifiedPostDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPostDTO.getTags().remove(jpaTag);
        modifiedPostDTO.getTags().add(
            new TagDTO()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        forumService.diffMerge(postDTO, modifiedPostDTO);

        PostDTO updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(modifiedPostDTO, updatedPostDTO);
    }

    @Benchmark
    public void benchmarkDiffMergeRemoveParentPostComment() {
        List<PostCommentDTO> replies = modifiedPostDTO.getReplies();
        assertEquals(2, replies.size());

        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        forumService.diffMerge(postDTO, modifiedPostDTO);
    }
}

