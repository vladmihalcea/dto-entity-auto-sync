package com.vladmihalcea.test;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.TagDto;
import com.vladmihalcea.phd.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.util.UserContext;
import com.vladmihalcea.test.config.BenchmarkTestData;
import com.vladmihalcea.test.config.JpaConfiguration;
import com.vladmihalcea.test.config.SpringWebConfiguration;
import com.vladmihalcea.test.controller.RestCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@SpringBootTest(
    classes = {
        JpaConfiguration.class,
        SpringWebConfiguration.class,
        ForumApplication.class,
        BenchmarkTestData.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JpaRestTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    public JpaRestTest(@LocalServerPort int port) {
        this.restCaller = new RestCaller(
            port,
            new RestTemplate()
        );
    }

    @BeforeEach
    public void init() {
        if(!testData.isDataValid()) {
            testData.createTestData();
        }
    }

    @Test
    public void testDiffMerge() {
        Long postId = 1L;
        PostDto postDTO = restCaller.loadPostDTO(postId);

        UserContext.setCurrentUser(postDTO.getAuthor());

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
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow()
        );
        postDTO.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        restCaller.updatePost(postDTO);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
    }
}