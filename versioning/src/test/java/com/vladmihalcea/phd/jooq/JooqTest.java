package com.vladmihalcea.phd.jooq;

import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.jooq.dao.PostDaoImpl;
import com.vladmihalcea.phd.jooq.dao.UserDaoImpl;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JooqConfiguration.class)
public class JooqTest extends AbstractSpringTest {
    
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ForumService forumService;

    @Autowired
    private PostDaoImpl postDAO;

    @Autowired
    private UserDaoImpl userDAO;

    @Autowired
    private DSLContext ctx;

    private Long postId;

    @Override
    protected void deleteAll() {
        ctx.execute("DELETE FROM post_tags");
        ctx.execute("DELETE FROM post_comments");
        ctx.execute("DELETE FROM posts");
        ctx.execute("DELETE FROM tags");
        ctx.execute("DELETE FROM users");
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
                    Spring and Hibernate can help you do the auto-sync
                    between DTOs and JPA entities
                    """)
                .setAuthor(vlad)
                .setReplies(List.of())
                .setTags(new ArrayList<>());

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

            short version = post.getVersion();

            version = forumService.createPostComment(postId, version, johnReply);
            version = forumService.createPostComment(postId, version, janeReply);

            TagDto spring = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("Spring");
            TagDto jpa = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("JPA");
            TagDto hibernate = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("Hibernate");
            TagDto mapstruct = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("MapStruct");

            version = forumService.createTag(postId, version, spring);
            version = forumService.createTag(postId, version, jpa);
            version = forumService.createTag(postId, version, hibernate);
            forumService.createTag(postId, version, mapstruct);
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
        LOGGER.info("Sync DTOs and jOOQ state");
        PostDto postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("Single-Op DTO Sync Pattern").upvote();
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
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        version = forumService.removeTag(postId, version, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        version = forumService.createTag(postId, version, jakarta);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
        assertEquals(version, updatedPostDTO.getVersion());
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

        forumService.removePostComment(postId, postDTO.getVersion(), parentCommentDTO.getId());

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }

    @Test
    public void testDiffMergeSync() {
        LOGGER.info("Sync DTOs and jOOQ state via a single Diff-Merge call");

        PostDto originalPost = forumService.loadPostDTO(postId);
        PostDto modifiedPost = JsonUtil.clone(originalPost);

        modifiedPost.setTitle("Single-Op DTO Sync Pattern").upvote();

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

        LOGGER.info("Update PostDTO aggregate using Diff-Merge");
        short version = forumService.diffMerge(originalPost, modifiedPost);

        PostDto updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
        assertEquals(originalPost.getVersion() + 1, version);
        assertEquals(version, updatedPostDTO.getVersion());
    }

    @Test
    public void testDiffMergeRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO via Diff-Merge and verify children are also removed");

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
