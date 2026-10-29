package com.vladmihalcea.phd.common;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.TagDao;
import com.vladmihalcea.phd.common.dao.UserDao;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that any write to the Post aggregate increments the post version,
 * so that a write based on a stale version is rejected, even when it changes
 * a child row that the concurrent write did not change.
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractOptimisticLockingTest extends AbstractSpringTest {

    private static final String JOHN_REPLY = "Interesting topic, but I would like to see more examples";
    private static final String JANE_REPLY = "Can you apply this pattern to other platforms?";

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDao userDAO;

    @Autowired
    private PostDao postDAO;

    @Autowired
    private PostCommentDao postCommentDAO;

    @Autowired
    private TagDao tagDAO;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    private Long postId;

    @Override
    protected void deleteAll() {
        jdbcTemplate().execute("DELETE FROM post_tags");
        jdbcTemplate().execute("DELETE FROM post_comments");
        jdbcTemplate().execute("DELETE FROM posts");
        jdbcTemplate().execute("DELETE FROM tags");
        jdbcTemplate().execute("DELETE FROM users");
    }

    private JdbcTemplate jdbcTemplate() {
        if (jdbcTemplate == null) {
            jdbcTemplate = new JdbcTemplate(dataSource);
        }
        return jdbcTemplate;
    }

    @Override
    public void afterInit() {
        postId = transactionTemplate.execute(transactionStatus -> {
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
            userDAO.insert(vlad);
            userDAO.insert(john);
            userDAO.insert(jane);

            PostDto post = new PostDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("Optimistic locking at the aggregate root level")
                .setAuthor(vlad)
                .setReplies(List.of())
                .setTags(new ArrayList<>());
            postDAO.insert(post);

            PostCommentDto johnReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent(JOHN_REPLY)
                .setAuthor(john);
            PostCommentDto janeReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent(JANE_REPLY)
                .setAuthor(jane)
                .setParentId(johnReply.getId());
            postCommentDAO.insert(post.getId(), johnReply);
            postCommentDAO.insert(post.getId(), janeReply);

            TagDto spring = new TagDto().setId(TimeSortedIdentifierGenerator.generate()).setName("Spring");
            tagDAO.upsert(spring);
            postDAO.linkTag(post.getId(), spring.getId());

            return post.getId();
        });
    }

    @Test
    public void testStaleDiffMergeOfAnotherChildIsRejected() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);
        assertEquals(0, alicePost.getVersion());

        LOGGER.info("Alice changes the first comment only");
        PostDto aliceModifiedPost = JsonUtil.clone(alicePost);
        aliceModifiedPost.getReplies().get(0).setContent("Alice's edit");
        assertEquals(1, forumService.diffMerge(alicePost, aliceModifiedPost));

        LOGGER.info("Bob changes the second comment, which Alice did not change");
        PostDto bobModifiedPost = JsonUtil.clone(bobPost);
        bobModifiedPost.getReplies().get(1).setContent("Bob's edit");
        assertThrows(
            OptimisticLockingFailureException.class,
            () -> forumService.diffMerge(bobPost, bobModifiedPost)
        );

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals("Alice's edit", post.getReplies().get(0).getContent());
        assertEquals(JANE_REPLY, post.getReplies().get(1).getContent());
    }

    @Test
    public void testMultiOpsWriteInvalidatesDiffMergeSnapshot() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);

        LOGGER.info("Alice changes a comment using a Multi-Ops call");
        PostCommentDto aliceReply = alicePost.getReplies().get(0).setContent("Alice's edit");
        assertEquals(1, forumService.updatePostComment(postId, alicePost.getVersion(), aliceReply));

        LOGGER.info("Bob adds a tag using Diff-Merge");
        PostDto bobModifiedPost = JsonUtil.clone(bobPost);
        bobModifiedPost.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("jOOQ")
        );
        assertThrows(
            OptimisticLockingFailureException.class,
            () -> forumService.diffMerge(bobPost, bobModifiedPost)
        );

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals(1, post.getTags().size());
    }

    @Test
    public void testStaleMultiOpsWriteIsRejected() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);

        LOGGER.info("Alice changes the post title using Diff-Merge");
        PostDto aliceModifiedPost = JsonUtil.clone(alicePost).setTitle("Alice's title");
        assertEquals(1, forumService.diffMerge(alicePost, aliceModifiedPost));

        LOGGER.info("Bob removes a comment using a Multi-Ops call");
        Long janeReplyId = bobPost.getReplies().get(1).getId();
        assertThrows(
            OptimisticLockingFailureException.class,
            () -> forumService.removePostComment(postId, bobPost.getVersion(), janeReplyId)
        );

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals(2, post.getReplies().size());
    }

    @Test
    public void testConcurrentDiffMergeTransactions() throws Exception {
        PostDto originalPost = forumService.loadPostDTO(postId);

        PostDto aliceModifiedPost = JsonUtil.clone(originalPost);
        aliceModifiedPost.getReplies().get(0).setContent("Alice's edit");

        PostDto bobModifiedPost = JsonUtil.clone(originalPost);
        bobModifiedPost.getReplies().get(1).setContent("Bob's edit");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<Short> bobWrite = transactionTemplate.execute(transactionStatus -> {
                LOGGER.info("Alice updates the post row first, so her transaction holds its row lock");
                forumService.diffMerge(originalPost, aliceModifiedPost);

                LOGGER.info("Bob passed the same version check and blocks on the post row lock");
                Future<Short> write = executorService.submit(
                    () -> forumService.diffMerge(originalPost, bobModifiedPost)
                );
                awaitLockWait();
                return write;
            });

            LOGGER.info("After Alice commits, Bob's version predicate no longer matches the post row");
            ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> bobWrite.get(10, TimeUnit.SECONDS)
            );
            assertInstanceOf(OptimisticLockingFailureException.class, failure.getCause());
        } finally {
            executorService.shutdownNow();
        }

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals("Alice's edit", post.getReplies().get(0).getContent());
        assertEquals(JANE_REPLY, post.getReplies().get(1).getContent());
    }

    private void awaitLockWait() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (jdbcTemplate().queryForObject(
                "SELECT count(*) FROM pg_locks WHERE NOT granted", Integer.class) == 0) {
            if (System.nanoTime() > deadline) {
                fail("The concurrent transaction did not block on the post row lock");
            }
            Thread.onSpinWait();
        }
    }
}
