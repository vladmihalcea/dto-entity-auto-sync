package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.jmh.rest.JpaBenchmarkSetup;
import com.vladmihalcea.phd.jpa.service.ForumService;
import org.openjdk.jmh.annotations.*;
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
public class JpaBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private JpaBenchmarkSetup benchmarkSetup;

    private Long postId;

    private PostDto postDTO;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JpaConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.main.web-application-type", "none"));
        context = app.run();

        forumService   = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JpaBenchmarkSetup.class);
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
        postDTO = forumService.loadPostDTO(postId);
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public void benchmarkLoadDto() {
        forumService.loadPostDTO(postId);
    }

    /**
     * Mirrors {@code SpringDataJPADTO2EntityTest#testDtoToEntitySync}:
     * loads the post DTO, modifies title / votes / comments / tags, persists
     * the changes and reloads to verify the round-trip.
     */
    @Benchmark
    public void benchmarkDtoToEntitySync() {
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
                .filter(t -> t.getName().equals("JPA"))
                .findFirst()
                .orElseThrow()
        );
        postDTO.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        forumService.updatePost(postDTO);
    }

    /**
     * Mirrors {@code SpringDataJPADTO2EntityTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (and its child) and persists.
     */
    @Benchmark
    public void benchmarkRemoveParentPostComment() {
        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        forumService.updatePost(postDTO);
    }
}

