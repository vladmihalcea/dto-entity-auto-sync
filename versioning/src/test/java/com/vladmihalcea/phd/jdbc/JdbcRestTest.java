package com.vladmihalcea.phd.jdbc;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.SpringRestConfiguration;
import com.vladmihalcea.phd.common.controller.RestCaller;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import com.vladmihalcea.phd.jdbc.dao.PostDaoImpl;
import com.vladmihalcea.phd.jdbc.dao.UserDaoImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Vlad Mihalcea
 */
@SpringBootTest(
    classes = {
        JdbcConfiguration.class,
        SpringRestConfiguration.class,
        ForumApplication.class,
        BenchmarkTestData.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JdbcRestTest extends AbstractSpringTest {

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

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    private Long postId;

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
        afterInit();
    }

    public void afterInit() {
        postId = transactionTemplate.<Long>execute(transactionStatus -> {
            List<UserDto> users = jdbcTemplate.query("""
                select id, email, first_name, last_name
                from users
                """, (rs, rowNum) -> new UserDto()
                    .setId(rs.getLong("id"))
                    .setEmail(rs.getString("email"))
                    .setFirstName(rs.getString("first_name"))
                    .setLastName(rs.getString("last_name")));

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
        LOGGER.info("Sync DTOs and JDBC state");
        PostDto postDTO = restCaller.loadPostDTO(postId);

        postDTO.setTitle("Multi-Ops DTO Sync Pattern").upvote();
        short version = restCaller.updatePost(postDTO);

        List<PostCommentDto> replies = postDTO.getReplies();
        PostCommentDto removed = replies.stream().filter(comment -> comment.getParentId() != null).findFirst().orElseThrow();
        replies.remove(removed);
        version = restCaller.removePostComment(postId, version, removed.getId());

        for (PostCommentDto reply : replies) {
            reply.upvote();
            version = restCaller.updatePostComment(postId, version, reply);
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
        LOGGER.info("Sync DTOs and JDBC state via a single Diff-Merge call");

        PostDto originalPost = restCaller.loadPostDTO(postId);
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

        LOGGER.info("Update PostDTO using diffMerge");
        long startNanos = System.nanoTime();
        short version = restCaller.diffMerge(originalPost, modifiedPost);
        LOGGER.info("Update PostDTO using diffMerge took: {} ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));

        PostDto updatedPostDTO = restCaller.loadPostDTO(postId);
        assertEquals(modifiedPost, updatedPostDTO);
        assertEquals(originalPost.getVersion() + 1, version);
        assertEquals(version, updatedPostDTO.getVersion());
    }

    @Test
    public void testStaleWriteIsRejectedWithPreconditionFailed() {
        PostDto alicePost = restCaller.loadPostDTO(postId);
        PostDto bobPost = restCaller.loadPostDTO(postId);

        PostDto aliceModifiedPost = JsonUtil.clone(alicePost);
        aliceModifiedPost.getReplies().getFirst().setContent("Alice's edit");
        restCaller.diffMerge(alicePost, aliceModifiedPost);

        PostCommentDto bobReply = bobPost.getReplies().getLast().setContent("Bob's edit");
        HttpClientErrorException failure = assertThrows(
            HttpClientErrorException.class,
            () -> restCaller.updatePostComment(postId, bobPost.getVersion(), bobReply)
        );
        assertEquals(HttpStatus.PRECONDITION_FAILED, failure.getStatusCode());
    }
}