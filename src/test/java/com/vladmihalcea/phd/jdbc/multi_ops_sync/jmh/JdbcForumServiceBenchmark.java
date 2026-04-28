package com.vladmihalcea.phd.jdbc.multi_ops_sync.jmh;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.multi_ops_sync.config.JdbcDTOManualSyncConfiguration;
import com.vladmihalcea.phd.jdbc.multi_ops_sync.service.ForumService;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures the execution time of the JDBC
 * {@link ForumService} for the two main test scenarios defined in
 * {@code JdbcDTOManualSyncTest}:
 * <ul>
 *   <li>{@link #benchmarkSingleOpDtoSync()} — mirrors {@code testMultiOpsDtoSync}</li>
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

        forumService   = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JdbcForumBenchmarkSetup.class);
    }

    @TearDown(Level.Trial)
    public void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    /** Recreates the standard fixture before every benchmark invocation. */
    @Setup(Level.Invocation)
    public void resetData() {
        benchmarkSetup.deleteAll();
        postId = benchmarkSetup.createTestData();
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    /**
     * Mirrors {@code JdbcDTOManualSyncTest#testMultiOpsDtoSync}:
     * loads the post DTO, modifies title / votes, removes a comment, upvotes
     * remaining comments, adds a new reply, swaps one tag.
     */
    @Benchmark
    public PostDTO benchmarkSingleOpDtoSync() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("Single-Op DTO Sync Pattern").upvote();
        forumService.updatePost(postDTO);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO removed = replies.removeFirst();
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

        // reload and return so JMH dead-code elimination cannot skip the call
        return forumService.loadPostDTO(postId);
    }

    /**
     * Mirrors {@code JdbcDTOManualSyncTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (which cascades to its
     * children) and verifies the post has no remaining replies.
     */
    @Benchmark
    public PostDTO benchmarkRemoveParentPostComment() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        forumService.removePostComment(parent.getId());

        return forumService.loadPostDTO(postId);
    }
}

