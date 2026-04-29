package com.vladmihalcea.phd.jooq.jmh;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqDTOManualSyncConfiguration;
import com.vladmihalcea.phd.jooq.service.ForumService;
import org.openjdk.jmh.annotations.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JMH benchmark that measures the execution time of the jOOQ
 * {@link ForumService} for the two main test scenarios defined in
 * {@code JooqDTOManualSyncTest}:
 * <ul>
 *   <li>{@link #benchmarkSingleOpDtoSync()} — mirrors {@code testMultiOpsDtoSync}</li>
 *   <li>{@link #benchmarkRemoveParentPostComment()} — mirrors {@code testRemoveParentPostComment}</li>
 * </ul>
 *
 * <p>The Spring context (jOOQ + TestContainers, no web server) is started once
 * per trial and the test dataset is rebuilt before every invocation so each
 * call always starts from the same known state.
 *
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JooqForumServiceBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private final ObjectMapper patchMapper = new ObjectMapper();
    private JooqForumBenchmarkSetup benchmarkSetup;

    private Long postId;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JooqDTOManualSyncConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        context = app.run();

        forumService = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JooqForumBenchmarkSetup.class);
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
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    /**
     * Mirrors {@code JooqDTOManualSyncTest#testMultiOpsDtoSync}:
     * loads the post DTO, modifies title / votes, removes a comment, upvotes
     * remaining comments, adds a new reply, swaps one tag.
     */
    @Benchmark
    public void benchmarkSingleOpDtoSync() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("Single-Op DTO Sync Pattern").upvote();
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

    /**
     * Mirrors {@code JooqDTOManualSyncTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (which cascades to its
     * children) and verifies the post has no remaining replies.
     */
    @Benchmark
    public void benchmarkRemoveParentPostComment() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        forumService.removePostComment(parent.getId());
    }

    @Benchmark
    public void benchmarkJsonPatchSync() {
        PostDTO originalPost = forumService.loadPostDTO(postId);
        PostDTO desiredPost  = forumService.loadPostDTO(postId);   // independent copy

        desiredPost.setTitle("Single-Op DTO Sync Pattern").upvote();

        List<PostCommentDTO> replies = desiredPost.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDTO::upvote);

        PostCommentDTO newReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDTO jpaTag = desiredPost.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        desiredPost.getTags().remove(jpaTag);
        desiredPost.getTags().add(
            new TagDTO()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        JsonPatch patch = JsonDiff.asJsonPatch(
            patchMapper.valueToTree(originalPost),
            patchMapper.valueToTree(desiredPost)
        );

        forumService.syncPost(originalPost, patch);
    }

    /**
     * Mirrors {@code JdbcDTOManualSyncTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (which cascades to its
     * children) and verifies the post has no remaining replies.
     */
    @Benchmark
    public void benchmarkJsonPatchRemoveParentPostComment() {
        PostDTO originalPost = forumService.loadPostDTO(postId);
        PostDTO desiredPost  = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = desiredPost.getReplies();
        assertEquals(2, replies.size());

        PostCommentDTO parentCommentDTO = replies.stream()
            .filter(comment -> comment.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.stream()
            .filter(child -> parentCommentDTO.getId().equals(child.getParentId()))
            .findAny()
            .orElseThrow();

        // desired state: no replies
        replies.clear();

        JsonPatch patch = JsonDiff.asJsonPatch(
            patchMapper.valueToTree(originalPost),
            patchMapper.valueToTree(desiredPost)
        );

        forumService.syncPost(originalPost, patch);
    }
}

