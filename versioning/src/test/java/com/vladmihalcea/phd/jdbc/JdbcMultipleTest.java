package com.vladmihalcea.phd.jdbc;

import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    JdbcConfiguration.class,
    BenchmarkTestData.class,
})
public class JdbcMultipleTest extends AbstractSpringTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ForumService forumService;

    @Autowired
    private BenchmarkTestData testData;

    @Override
    protected void deleteAll() {
        testData.deleteAll();
    }

    @Override
    public void afterInit() {
        testData.createTestData();
    }

    @Test
    public void testMultiOpsDtoSync() {
        for (long postId = 1; postId <= 20; postId++) {
            LOGGER.info("Sync DTOs and JDBC state");
            PostDto postDTO = forumService.loadPostDTO(postId);
            UserContext.setCurrentUser(postDTO.getAuthor());

            postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
            short version = forumService.updatePost(postDTO);

            List<PostCommentDto> replies = postDTO.getReplies();
            PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
            replies.remove(removed);
            version = forumService.removePostComment(postId, version, removed.getId());

            for (PostCommentDto r : replies) {
                r.upvote();
                version = forumService.updatePostComment(postId, version, r);
            }

            PostCommentDto newReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Does it work with jOOQ?")
                .setAuthor(UserContext.getCurrentUser())
                .setParentId(replies.getFirst().getId());
            replies.add(newReply);
            version = forumService.createPostComment(postId, version, newReply);

            TagDto jpaTag = postDTO.getTags().stream()
                .filter(t -> t.getName().startsWith("JPA"))
                .findFirst()
                .orElseThrow();
            postDTO.getTags().remove(jpaTag);
            version = forumService.removeTag(postId, version, jpaTag);

            Long tagId = TimeSortedIdentifierGenerator.generate();
            TagDto jakarta = new TagDto()
                .setId(tagId)
                .setName("Jakarta Persistence " + tagId);
            postDTO.getTags().add(jakarta);
            version = forumService.createTag(postId, version, jakarta);

            PostDto updatedPostDTO = forumService.loadPostDTO(postId);
            assertEquals(postDTO, updatedPostDTO);
            assertEquals(version, updatedPostDTO.getVersion());
        }
    }
}
