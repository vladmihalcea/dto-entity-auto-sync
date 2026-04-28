package com.vladmihalcea.phd.jooq.multi_ops_sync;

import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqDTOManualSyncConfiguration;
import com.vladmihalcea.phd.jooq.dao.UserDAO;
import com.vladmihalcea.phd.jooq.service.ForumService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JooqDTOManualSyncConfiguration.class)
public class JooqDTOManualSyncTest extends AbstractSpringTest {

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private DSLContext ctx;

    private Long postId;

    @Override
    protected void deleteAll() {
        ctx.execute("DELETE FROM post_tag");
        ctx.execute("DELETE FROM post_comments");
        ctx.execute("DELETE FROM posts");
        ctx.execute("DELETE FROM tags");
        ctx.execute("DELETE FROM users");
    }

    @Override
    public void afterInit() {
        UserDTO vlad = new UserDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setEmail("vlad@acme.com")
            .setFirstName("Vlad")
            .setLastName("Mihalcea");
        UserDTO john = new UserDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setEmail("john@acme.com")
            .setFirstName("John")
            .setLastName("Doe");
        UserDTO jane = new UserDTO()
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

        PostDTO post = new PostDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setTitle("DTO to Entity Auto-Sync")
            .setContent("""
                    Spring and Hibernate can help you do the aut-sync
                    between DTOs and JPA entities
                    """)
            .setAuthor(vlad)
            .setReplies(List.of())
            .setTags(new LinkedHashSet<>());

        forumService.createPost(post);
        postId = post.getId();

        PostCommentDTO johnReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Interesting topic, but I would like to see more examples")
            .setAuthor(john);
        PostCommentDTO janeReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Can you apply this pattern to other platforms?")
            .setAuthor(jane)
            .setParentId(johnReply.getId());

        forumService.createPostComment(postId, johnReply);
        forumService.createPostComment(postId, janeReply);

        TagDTO spring = new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("Spring");
        TagDTO jpa = new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("JPA");
        TagDTO hibernate = new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("Hibernate");
        TagDTO mapstruct = new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("MapStruct");

        forumService.createTag(postId, spring);
        forumService.createTag(postId, jpa);
        forumService.createTag(postId, hibernate);
        forumService.createTag(postId, mapstruct);
    }

    @Test
    public void testMultiOpsDtoSync() {
        LOGGER.info("Sync DTOs and jOOQ state");
        PostDTO postDTO = forumService.loadPostDTO(postId);

        postDTO.setTitle("Single-Op DTO Sync Pattern").upvote();
        forumService.updatePost(postDTO);

        List<PostCommentDTO> replies = postDTO.getReplies();
        PostCommentDTO removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        forumService.removePostComment(removed.getId());

        for (PostCommentDTO r : replies) {
            r.upvote();
            forumService.updatePostComment(r);
        }

        PostCommentDTO newReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        forumService.createPostComment(postId, newReply);

        TagDTO jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        forumService.removeTag(postId, jpaTag);

        TagDTO jakarta = new TagDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        forumService.createTag(postId, jakarta);

        PostDTO updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
    }

    @Test
    public void testRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO and see whether the children are also removed");
        PostDTO postDTO = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = postDTO.getReplies();
        assertEquals(2, replies.size());
        PostCommentDTO parentCommentDTO = replies.stream()
            .filter(comment -> comment.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.stream()
            .filter(child -> parentCommentDTO.getId().equals(child.getParentId()))
            .findAny()
            .orElseThrow();

        forumService.removePostComment(parentCommentDTO.getId());

        PostDTO updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }
}

