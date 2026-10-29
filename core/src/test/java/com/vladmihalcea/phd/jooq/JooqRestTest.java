package com.vladmihalcea.phd.jooq;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import com.vladmihalcea.phd.jooq.dao.PostDaoImpl;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@SpringBootTest(
    classes = {
        JooqConfiguration.class,
        SpringRestConfiguration.class,
        ForumApplication.class,
        BenchmarkTestData.class,
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JooqRestTest {
    
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ForumService forumService;

    @Autowired
    private PostDaoImpl postDAO;

    @Autowired
    private DSLContext ctx;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    private Long postId;

    public JooqRestTest(@LocalServerPort int port) {
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
        afterInit();
    }

    public void afterInit() {
        postId = transactionTemplate.<Long>execute(transactionStatus -> {
            List<UserDto> users = ctx.select(USERS_ID, USERS_EMAIL, USERS_FIRST_NAME, USERS_LAST_NAME)
                .from(USERS)
                .fetch(r -> new UserDto()
                    .setId(r.get(USERS_ID))
                    .setEmail(r.get(USERS_EMAIL))
                    .setFirstName(r.get(USERS_FIRST_NAME))
                    .setLastName(r.get(USERS_LAST_NAME)));

            UserDto vlad = users.stream().filter(u -> u.getEmail().equals("vlad@acme.com")).findFirst().get();

            UserDto john = users.stream().filter(u -> u.getEmail().equals("john@acme.com")).findFirst().get();

            UserDto jane = users.stream().filter(u -> u.getEmail().equals("jane@acme.com")).findFirst().get();

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
    public void testLoadPostDtoUsingMultiset() {
        LOGGER.info("Load a Post as a DTO mapped from the MULTISET JSON");
        // Both MS-JSON endpoints return the same document, so the typed-msjson benchmark block compares equal payloads
        JsonNode postDtoToNode = JsonUtil.JSON_MAPPER.readTree(restCaller.loadPostDTOUsingMultisetAsString(postId));
        JsonNode postJsonStringToNode = JsonUtil.JSON_MAPPER.readTree(restCaller.loadPostJsonString(postId));
        assertEquals(postJsonStringToNode, postDtoToNode);
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
        restCaller.removePostComment(postId, removed.getId());

        for (PostCommentDto reply : replies) {
            reply.upvote();
            restCaller.updatePostComment(postId, reply);
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
        LOGGER.info("Sync DTOs and jOOQ state via a single Diff-Merge call");

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

        LOGGER.info("Update PostDTO using diffMerge");
        long startNanos = System.nanoTime();
        restCaller.diffMerge(originalPost, modifiedPost);
        LOGGER.info("Update PostDTO using diffMerge took: {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
    }
}