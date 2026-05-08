package com.vladmihalcea.test;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.TagDto;
import com.vladmihalcea.phd.util.JsonUtil;
import com.vladmihalcea.phd.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.util.UserContext;
import com.vladmihalcea.test.config.BenchmarkTestData;
import com.vladmihalcea.test.config.JdbcConfiguration;
import com.vladmihalcea.test.config.SpringWebConfiguration;
import com.vladmihalcea.test.controller.RestCaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        JdbcConfiguration.class,
        SpringWebConfiguration.class,
        ForumApplication.class,
        BenchmarkTestData.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JdbcRestTest {

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    public JdbcRestTest(@LocalServerPort int port) {
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
        PostDto originalPost = restCaller.loadPostDTO(postId);

        UserContext.setCurrentUser(originalPost.getAuthor());

        PostDto modifiedPost = JsonUtil.clone(originalPost);

        modifiedPost.setTitle("Multi-Ops DTO Sync Pattern").upvote();

        List<PostCommentDto> replies = modifiedPost.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDto::upvote);

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDto jpaTag = modifiedPost.getTags().stream()
            .filter(t -> t.getName().startsWith("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPost.getTags().remove(jpaTag);
        modifiedPost.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        restCaller.diffMerge(originalPost, modifiedPost);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
    }
}