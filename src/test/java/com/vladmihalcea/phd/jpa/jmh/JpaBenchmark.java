package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.service.ForumService;
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
 * JMH benchmark that measures the execution time of the JPA
 * @author Vlad Mihalcea
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class JpaBenchmark {

    private ConfigurableApplicationContext context;
    private ForumService forumService;
    private JpaBenchmarkSetup benchmarkSetup;

    private Long postId;

    private PostDto postDTO;

    private RestCaller restCaller;

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

        forumService   = context.getBean(ForumService.class);
        benchmarkSetup = context.getBean(JpaBenchmarkSetup.class);

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
        forumService.loadPostDTOUsingMultiset(postId);
    }

    @Benchmark
    public void benchmarkDiffMergeSync() {
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

    @Benchmark
    public void benchmarkDiffMergeSyncUsingRest() {
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

        restCaller.updatePost(postDTO);
    }
}

