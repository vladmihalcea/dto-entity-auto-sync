package com.vladmihalcea.phd.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcDTOManualSyncConfiguration;
import com.vladmihalcea.phd.jdbc.dao.UserDAO;
import com.vladmihalcea.phd.jdbc.service.ForumService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the {@code jdbc.json_patch_sync} module.
 *
 * <p>Mirrors {@code jdbc.multi_ops_sync.JdbcDTOManualSyncTest} in terms of
 * fixture setup and assertions, but replaces the series of individual
 * {@link ForumService} calls with a single
 * {@link ForumService#syncPost(PostDTO, JsonPatch)} invocation that accepts an
 * RFC-6902 JSON Patch.
 *
 * <p>The patch is derived automatically using
 * {@link JsonDiff#asJsonPatch(com.fasterxml.jackson.databind.JsonNode,
 * com.fasterxml.jackson.databind.JsonNode)} by comparing the original
 * {@link PostDTO} snapshot against the in-memory desired state.
 *
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = JdbcDTOManualSyncConfiguration.class)
public class JdbcJsonPatchDTOManualSyncTest extends AbstractSpringTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /** Jackson 2 ObjectMapper — used only to build JSON Patches via JsonDiff. */
    private final ObjectMapper patchMapper = new ObjectMapper();

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long postId;

    // -------------------------------------------------------------------------
    // Setup / teardown
    // -------------------------------------------------------------------------

    @Override
    protected void deleteAll() {
        jdbcTemplate.execute("DELETE FROM post_tag");
        jdbcTemplate.execute("DELETE FROM post_comments");
        jdbcTemplate.execute("DELETE FROM posts");
        jdbcTemplate.execute("DELETE FROM tags");
        jdbcTemplate.execute("DELETE FROM users");
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

        forumService.createTag(postId, new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("Spring"));
        forumService.createTag(postId, new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("JPA"));
        forumService.createTag(postId, new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("Hibernate"));
        forumService.createTag(postId, new TagDTO().setId(TimeSortedIdentifierGenerator.generate()).setName("MapStruct"));
    }

    // -------------------------------------------------------------------------
    // Test cases
    // -------------------------------------------------------------------------

    /**
     * Mirrors {@code testJsonPatchSync}: the full mutation sequence
     * (title + vote, remove a comment, upvote remaining, add new reply, swap
     * tag) is expressed as a single JSON Patch and applied in one transaction.
     */
    @Test
    public void testJsonPatchSync() {
        LOGGER.info("Sync DTOs and JDBC state via a single JSON Patch");

        // --- build the desired post state in memory ---
        PostDTO originalPost = forumService.loadPostDTO(postId);
        PostDTO desiredPost  = forumService.loadPostDTO(postId);   // independent copy

        desiredPost.setTitle("Single-Op DTO Sync Pattern").upvote();

        List<PostCommentDTO> replies = desiredPost.getReplies();
        replies.removeIf(comment -> comment.getParentId() != null);
        replies.forEach(PostCommentDTO::upvote);

        PostCommentDTO newReply = new PostCommentDTO()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);

        TagDTO jpaTag = desiredPost.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        desiredPost.getTags().remove(jpaTag);
        desiredPost.getTags().add(
            new TagDTO()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Jakarta Persistence")
        );

        // --- generate the JSON Patch that transforms original → desired ---
        JsonPatch patch = JsonDiff.asJsonPatch(
            patchMapper.valueToTree(originalPost),
            patchMapper.valueToTree(desiredPost)
        );
        LOGGER.info("Generated JSON Patch: {}", patch);

        // --- apply all changes in one @Transactional call ---
        forumService.syncPost(originalPost, patch);

        // --- verify ---
        PostDTO updatedPostDTO = forumService.loadPostDTO(postId);
        assertEquals(desiredPost, updatedPostDTO);
    }

    /**
     * Mirrors {@code testJsonPatchRemoveParentPostComment}: removing the parent comment
     * via a JSON Patch causes the child to be cascade-deleted as well.
     */
    @Test
    public void testJsonPatchRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO via JSON Patch and verify children are also removed");

        PostDTO originalPost = forumService.loadPostDTO(postId);
        PostDTO desiredPost  = forumService.loadPostDTO(postId);

        List<PostCommentDTO> replies = desiredPost.getReplies();
        assertEquals(2, replies.size());

        PostCommentDTO parentCommentDTO = replies.stream()
            .filter(comment -> comment.getParentId() == null)
            .findFirst()
            .orElseThrow();
        replies.stream()
            .filter(child -> parentCommentDTO.getId().equals(child.getParentId()))
            .findAny()
            .orElseThrow();

        // desired state: no replies
        replies.clear();

        JsonPatch patch = JsonDiff.asJsonPatch(
            patchMapper.valueToTree(originalPost),
            patchMapper.valueToTree(desiredPost)
        );
        LOGGER.info("Generated JSON Patch: {}", patch);

        forumService.syncPost(originalPost, patch);

        PostDTO updatedPostDTO = forumService.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }
}

