package com.vladmihalcea.phd.jdbc;

import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import com.vladmihalcea.phd.jdbc.dao.PostDaoImpl;
import com.vladmihalcea.phd.jdbc.dao.UserDaoImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcConfiguration.class)
public class JdbcTest extends AbstractSpringTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ForumService forumService;

    @Autowired
    private PostDaoImpl postDAO;

    @Autowired
    private UserDaoImpl userDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long postId;

    @Override
    protected void deleteAll() {
        jdbcTemplate.execute("DELETE FROM post_tags");
        jdbcTemplate.execute("DELETE FROM post_comments");
        jdbcTemplate.execute("DELETE FROM posts");
        jdbcTemplate.execute("DELETE FROM tags");
        jdbcTemplate.execute("DELETE FROM users");
    }

    @Override
    public void afterInit() {
        postId = transactionTemplate.<Long>execute(transactionStatus -> {
            UserDto vlad = new UserDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setEmail("vlad@acme.com")
                .setFirstName("Vlad")
                .setLastName("Mihalcea");
            UserDto john = new UserDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setEmail("john@acme.com")
                .setFirstName("John")
                .setLastName("Doe");
            UserDto jane = new UserDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setEmail("jane@acme.com")
                .setFirstName("Jane")
                .setLastName("Doe");

            transactionTemplate.execute(status -> {
                userDAO.insert(vlad);
                userDAO.insert(john);
                userDAO.insert(jane);
                return null;
            });

            UserContext.setCurrentUser(vlad);

            PostDto post = new PostDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("""
                    Spring and Hibernate can help you do the aut-sync
                    between DTOs and JPA entities
                    """)
                .setAuthor(vlad)
                .setReplies(List.of())
                .setTags(new LinkedHashSet<>());

            postDAO.insert(post);
            postId = post.getId();

            PostCommentDto johnReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Interesting topic, but I would like to see more examples")
                .setAuthor(john);
            PostCommentDto janeReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Can you apply this pattern to other platforms?")
                .setAuthor(jane)
                .setParentId(johnReply.getId());

            forumService.createPostComment(postId, johnReply);
            forumService.createPostComment(postId, janeReply);

            TagDto spring = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("Spring");
            TagDto jpa = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("JPA");
            TagDto hibernate = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("Hibernate");
            TagDto mapstruct = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("MapStruct");

            forumService.createTag(postId, spring);
            forumService.createTag(postId, jpa);
            forumService.createTag(postId, hibernate);
            forumService.createTag(postId, mapstruct);
            return post.getId();
        });
    }

    @Test
    public void testLoadDtoUsingMultiset() {
        LOGGER.info("Load PostDTO using findById and MULTISET");
        PostDto defaultPostDTO = forumService.loadPostDTO(postId);
        PostDto multiSetPostDTO = forumService.loadPostDTOUsingMultiset(postId);
        JsonNode multiSetPostJsonNode = forumService.loadPostJsonNodeUsingMultiset(postId);
        PostDto multiSetJsonNodeToPostDTO = JsonUtil.JSON_MAPPER.convertValue(multiSetPostJsonNode, PostDto.class);
        String multiSetPostJsonString = forumService.loadPostJsonStringUsingMultiset(postId);
        PostDto multiSetJsonStringToPostDTO = JsonUtil.JSON_MAPPER.readValue(multiSetPostJsonString, PostDto.class);

        assertEquals(defaultPostDTO, multiSetPostDTO);
        assertEquals(multiSetPostDTO, multiSetJsonNodeToPostDTO);
        assertEquals(multiSetJsonNodeToPostDTO, multiSetJsonStringToPostDTO);
    }

    @Test
    public void testMultiOpsDtoSync() {
        LOGGER.info("Sync DTOs and JDBC state");
        PostDto postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        forumService.updatePost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        forumService.removePostComment(removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            forumService.updatePostComment(r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        forumService.createPostComment(postId, newReply);

        TagDto jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        forumService.removeTag(postId, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        forumService.createTag(postId, jakarta);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
    }

    @Test
    public void testRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO and see whether the children are also removed");
        PostDto postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDto> replies = postDTO.getReplies();
        assertEquals(2, replies.size());
        PostCommentDto parentCommentDTO = replies.stream()
            .filter(comment -> comment.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.stream()
            .filter(child -> parentCommentDTO.getId().equals(child.getParentId()))
            .findAny()
            .orElseThrow();

        forumService.removePostComment(parentCommentDTO.getId());

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }

    @Test
    public void testDiffMergeSync() {
        LOGGER.info("Sync DTOs and JDBC state via a single JSON Patch");

        PostDto originalPost = forumService.loadPostDTO(postId);
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
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        modifiedPost.getTags().remove(jpaTag);
        modifiedPost.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        forumService.diffMerge(originalPost, modifiedPost);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
    }

    @Test
    public void testDiffMergeRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO via JSON Patch and verify children are also removed");

        PostDto originalPost = forumService.loadPostDTO(postId);
        PostDto modifiedPost = JsonUtil.clone(originalPost);

        List<PostCommentDto> replies = modifiedPost.getReplies();
        assertEquals(2, replies.size());

        PostCommentDto parent = replies.stream()
            .filter(c -> c.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.remove(parent);

        forumService.diffMerge(originalPost, modifiedPost);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }
}
