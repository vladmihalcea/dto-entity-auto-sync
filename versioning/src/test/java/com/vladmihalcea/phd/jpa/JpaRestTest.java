package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringWebConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.BenchmarkRestTemplate;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private UserMapper userMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    private Long postId;

    public JpaRestTest(@LocalServerPort int port) {
        this.restCaller = new RestCaller(
            port,
            BenchmarkRestTemplate.create()
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
            List<User> users = entityManager.createQuery("select u from User u").getResultList();

            User vlad = users.stream().filter(u -> u.getEmail().equals("vlad@acme.com")).findFirst().get();
            User john = users.stream().filter(u -> u.getEmail().equals("john@acme.com")).findFirst().get();
            User jane = users.stream().filter(u -> u.getEmail().equals("jane@acme.com")).findFirst().get();


            UserContext.setCurrentUser(
                userMapper.toDTO(vlad)
            );

            PostComment parentPostComment = new PostComment()
                .setContent("Interesting topic, but I would like to see more examples")
                .setAuthor(john);

            PostComment childPostComment = new PostComment()
                .setContent("Can you apply this pattern to other platforms?")
                .setAuthor(jane)
                .setParent(parentPostComment);

            Post post = new Post()
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("""
                        Spring and Hibernate can help you do the auto-sync
                        between DTOs and JPA entities
                        """)
                .setAuthor(vlad)
                .addComment(parentPostComment)
                .addComment(childPostComment)
                .addTag(new Tag().setName("Spring"))
                .addTag(new Tag().setName("JPA"))
                .addTag(new Tag().setName("Hibernate"))
                .addTag(new Tag().setName("MapStruct"));

            entityManager.persist(post);

            return post.getId();
        });
    }

    @Test
    public void testLoadPostJsonUsingMultiset() {
        LOGGER.info("Load a Post as a JSON document using jOOQ's MULTISET");
        PostDto postDTO = restCaller.loadPostDTO(postId);
        JsonNode postViewToNode = restCaller.loadPostDTOUsingMultiset(postId);
        // Parse the serialized DTO, like the HTTP response, so that both trees use the same numeric node types
        JsonNode postDTOToNode = JsonUtil.JSON_MAPPER.readTree(JsonUtil.JSON_MAPPER.writeValueAsString(postDTO));

        assertEquals(postDTOToNode, postViewToNode);
    }

    @Test
    public void testMultiOpsDtoSync() {
        LOGGER.info("Sync DTOs and JPA entity state through the per-change Multi-Ops calls");
        PostDto postDTO = restCaller.loadPostDTO(postId);

        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        short version = restCaller.patchPost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        version = restCaller.removePostComment(postId, version, removed.getId());

        for (PostCommentDto r : replies) {
            r.upvote();
            version = restCaller.updatePostComment(postId, version, r);
        }

        PostCommentDto newReply = new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with jOOQ?")
            .setAuthor(UserContext.getCurrentUser())
            .setParentId(replies.getFirst().getId());
        replies.add(newReply);
        version = restCaller.createPostComment(postId, version, newReply);

        TagDto jpaTag = postDTO.getTags().stream()
            .filter(t -> t.getName().equals("JPA"))
            .findFirst()
            .orElseThrow();
        postDTO.getTags().remove(jpaTag);
        version = restCaller.removeTag(postId, version, jpaTag);

        TagDto jakarta = new TagDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setName("Jakarta Persistence");
        postDTO.getTags().add(jakarta);
        version = restCaller.createTag(postId, version, jakarta);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
        assertEquals(version, updatedPostDTO.getVersion());
    }

    @Test
    public void testDiffMergeSync() {
        LOGGER.info("Sync DTOs and JPA entity state");
        PostDto postDTO = restCaller.loadPostDTO(postId);

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

        LOGGER.info("Update JPA entity state from PostDTO");
        long startNanos = System.nanoTime();
        short version = restCaller.updatePost(postDTO);
        LOGGER.info("Update PostDTO using diffMerge took: {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));

        LOGGER.info("Reload PostDTO to check the modifications");
        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(postDTO, updatedPostDTO);
        assertEquals(version, updatedPostDTO.getVersion());
    }

    @Test
    public void testRemoveParentPostComment() {
        LOGGER.info("Remove a parent PostCommentDTO and see whether the children are also removed");
        PostDto postDTO = restCaller.loadPostDTO(postId);

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
        replies.remove(parentCommentDTO);

        restCaller.updatePost(postDTO);

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertTrue(updatedPostDTO.getReplies().isEmpty());
    }

    @Test
    public void testStaleWriteIsRejectedWithPreconditionFailed() {
        PostDto alicePost = restCaller.loadPostDTO(postId);
        PostDto bobPost = restCaller.loadPostDTO(postId);

        alicePost.getReplies().getFirst().setContent("Alice's edit");
        restCaller.updatePost(alicePost);

        bobPost.getReplies().getLast().setContent("Bob's edit");
        HttpClientErrorException failure = assertThrows(
            HttpClientErrorException.class,
            () -> restCaller.updatePost(bobPost)
        );
        assertEquals(HttpStatus.PRECONDITION_FAILED, failure.getStatusCode());
    }
}