package com.vladmihalcea.phd.jooq;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.jooq.dao.PostDaoImpl;
import com.vladmihalcea.phd.jooq.dao.UserDaoImpl;
import com.vladmihalcea.phd.jooq.service.JooqForumService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@SpringBootTest(
    classes = {
        JooqConfiguration.class,
        SpringWebConfiguration.class,
        ForumApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JooqRestTest extends AbstractSpringTest {
    
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private JooqForumService forumService;

    @Autowired
    private PostDaoImpl postDAO;

    @Autowired
    private UserDaoImpl userDAO;

    @Autowired
    private DSLContext ctx;

    private final RestCaller restCaller;

    private Long postId;

    public JooqRestTest(@LocalServerPort int port) {
        this.restCaller = new RestCaller(
            port,
            new RestTemplate()
        );
    }

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
    public void testLoadPostJsonUsingMultiset() {
        LOGGER.info("Load a Post as a JSON document using jOOQ's MULTISET");
        JsonNode postJsonNode = restCaller.loadPostJsonNode(postId);
        String postJsonString = restCaller.loadPostJsonString(postId);
        JsonNode postJsonStringToNode = JsonUtil.JSON_MAPPER.readTree(postJsonString);
        assertEquals(postJsonNode, postJsonStringToNode);
    }

    @Test
    public void testMultiOpsDtoSync() {
        LOGGER.info("Sync DTOs and jOOQ state");
        PostDto postDTO = restCaller.loadPostDTO(postId);

        postDTO.setTitle("Single-Op DTO Sync Pattern").upvote();
        restCaller.updatePost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        restCaller.removePostComment(removed.getId());

        for (PostCommentDto reply : replies) {
            reply.upvote();
            restCaller.updatePostComment(reply);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        restCaller.createPostComment(postId, newReply);

        TagDto jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        restCaller.removeTag(postId, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        restCaller.createTag(postId, jakarta);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
    }

    @Test
    public void testDiffMergeSync() {
        LOGGER.info("Sync DTOs and jOOQ state via a single JSON Patch");

        PostDto originalPost = restCaller.loadPostDTO(postId);
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

        restCaller.diffMerge(originalPost, modifiedPost);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
    }
}