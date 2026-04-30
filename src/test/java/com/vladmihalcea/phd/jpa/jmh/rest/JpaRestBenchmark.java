package com.vladmihalcea.phd.jpa.jmh.rest;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
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
public class JpaRestBenchmark {

    private ConfigurableApplicationContext context;
    private JpaBenchmarkSetup benchmarkSetup;
    private final RestTemplate restTemplate = new RestTemplate();

    private int port;
    private Long postId;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void startContext() {
        SpringApplication app = new SpringApplication(
            JpaConfiguration.class,
            SpringWebConfiguration.class,
            ForumApplication.class
        );
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.setDefaultProperties(Map.of("server.port", "0"));
        context = app.run();

        port = context.getBean(Environment.class)
            .getRequiredProperty("local.server.port", Integer.class);
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
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private PostDto loadPostDTO() {
        return restTemplate.getForObject(
            baseUrl() + "/post?postId={postId}",
            PostDto.class,
            postId
        );
    }

    private void updatePost(PostDto postDTO) {
        restTemplate.postForObject(
            baseUrl() + "/post",
            postDTO,
            Void.class
        );
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    @Benchmark
    public PostDto benchmarkDtoToEntitySync() {
        PostDto postDTO = loadPostDTO();

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

        updatePost(postDTO);

        // reload and return so JMH dead-code elimination cannot skip the call
        return loadPostDTO();
    }

    /**
     * Mirrors {@code SpringDataJPADTO2EntityRestTest#testRemoveParentPostComment}:
     * loads the post DTO, removes the root comment (and its child) and persists.
     */
    @Benchmark
    public PostDto benchmarkRemoveParentPostComment() {
        PostDto postDTO = loadPostDTO();

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        updatePost(postDTO);

        return loadPostDTO();
    }
}


