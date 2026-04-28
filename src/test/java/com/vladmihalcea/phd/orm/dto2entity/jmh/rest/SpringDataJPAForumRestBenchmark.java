package com.vladmihalcea.phd.orm.dto2entity.jmh.rest;

import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.orm.dto2entity.config.SpringDataJPADTO2EntityConfiguration;
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
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark that measures the HTTP response time of the {@code loadPostDTO}
 * and {@code updatePost} REST endpoints for the two main test scenarios:
 * <ul>
 *   <li>{@link #benchmarkDtoToEntitySync()} — mirrors {@code testDtoToEntitySync}</li>
 *   <li>{@link #benchmarkRemoveParentPostComment()} — mirrors {@code testRemoveParentPostComment}</li>
 * </ul>
 *
 * <p>The Spring Boot context (web server + JPA + TestContainers) is started once
 * per trial and the test dataset is rebuilt before every measurement iteration
 * so each invocation always starts from the same known state.
 *
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class SpringDataJPAForumRestBenchmark {

    private ConfigurableApplicationContext context;
    private SpringDataJPAForumBenchmarkSetup benchmarkSetup;
    private final RestTemplate restTemplate = new RestTemplate();

    private int port;
    private Long postId;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            SpringDataJPADTO2EntityConfiguration.class,
            SpringWebConfiguration.class
        );
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setDefaultProperties(Map.of("server.port", "0"));
        context = app.run();

        port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);
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
    // Helpers
    // -------------------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private PostDTO loadPostDTO() {
        return restTemplate.getForObject(
            baseUrl() + "/post?postId={postId}",
            PostDTO.class,
            postId
        );
    }

    private void updatePost(PostDTO postDTO) {
        restTemplate.postForObject(
            baseUrl() + "/post",
            postDTO,
            Void.class
        );
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    /**
     * Mirrors {@code SpringDataJPADTO2EntityRestTest#testDtoToEntitySync}:
     * loads the post DTO, modifies title / votes / comments / tags, persists
     * the changes and reloads to verify round-trip correctness.
     */
    @Benchmark
    public PostDTO benchmarkDtoToEntitySync() {
        PostDTO postDTO = loadPostDTO();

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

        updatePost(postDTO);

        // reload and return so JMH dead-code elimination cannot skip the call
        return loadPostDTO();
    }

    /**
     * Mirrors {@code SpringDataJPADTO2EntityRestTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (and its child) and persists.
     */
    @Benchmark
    public PostDTO benchmarkRemoveParentPostComment() {
        PostDTO postDTO = loadPostDTO();

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        updatePost(postDTO);

        return loadPostDTO();
    }
}


