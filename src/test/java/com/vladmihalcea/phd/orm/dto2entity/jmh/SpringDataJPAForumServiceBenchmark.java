package com.vladmihalcea.phd.orm.dto2entity.jmh;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.orm.dto2entity.config.SpringDataJPADTO2EntityConfiguration;
import com.vladmihalcea.phd.orm.dto2entity.jmh.rest.SpringDataJPAForumBenchmarkSetup;
import com.vladmihalcea.phd.orm.dto2entity.service.ForumService;
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
 * JMH benchmark that measures the execution time of {@link ForumService}
 * for the two main test scenarios defined in
 * {@code SpringDataJPADTO2EntityTest}:
 * <ul>
 *   <li>{@link #benchmarkDtoToEntitySync()} — mirrors {@code testDtoToEntitySync}</li>
 *   <li>{@link #benchmarkRemoveParentPostComment()} — mirrors {@code testRemoveParentPostComment}</li>
 * </ul>
 *
 * <p>The Spring context (JPA + TestContainers, no web server) is started once
 * per trial and the test dataset is rebuilt before every invocation so each
 * call always starts from the same known state.
 *
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class SpringDataJPAForumServiceBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private SpringDataJPAForumBenchmarkSetup benchmarkSetup;

    private Long postId;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            SpringDataJPADTO2EntityConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        context = app.run();

        forumService   = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(SpringDataJPAForumBenchmarkSetup.class);
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
     * Mirrors {@code SpringDataJPADTO2EntityTest#testDtoToEntitySync}:
     * loads the post DTO, modifies title / votes / comments / tags, persists
     * the changes and reloads to verify the round-trip.
     */
    @Benchmark
    public PostDTO benchmarkDtoToEntitySync() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("DTO to Entity Auto-Sync Pattern").upvote();

        List<PostCommentDTO> replies = postDTO.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDTO::upvote);
        replies.add(
            new PostCommentDTO()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with Entity Framework?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId())
        );
        postDTO.getTags().remove(
            postDTO.getTags().stream()
                .filter(t -> t.getName().equals("JPA"))
                .findFirst()
                .orElseThrow()
        );
        postDTO.getTags().add(
            new TagDTO()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        forumService.updatePost(postDTO);

        // reload and return so JMH dead-code elimination cannot skip the call
        return forumService.loadPostDTO(postId);
    }

    /**
     * Mirrors {@code SpringDataJPADTO2EntityTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (and its child) and persists.
     */
    @Benchmark
    public PostDTO benchmarkRemoveParentPostComment() {
        PostDTO postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        forumService.updatePost(postDTO);

        return forumService.loadPostDTO(postId);
    }
}

