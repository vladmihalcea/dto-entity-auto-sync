package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.service.ForumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that the {@link com.vladmihalcea.phd.jpa.domain.RootAware} listeners increment
 * the post version when only a child entity changes, so that a write based on a stale
 * version cannot overwrite the changes of a concurrent write.
 *
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JpaConfiguration.class)
public class JpaOptimisticLockingTest extends AbstractSpringTest {

    private static final String JOHN_REPLY = "Interesting topic, but I would like to see more examples";
    private static final String JANE_REPLY = "Can you apply this pattern to other platforms?";

    @Autowired
    private ForumService forumService;

    private Long postId;

    @Override
    public void afterInit() {
        postId = transactionTemplate.execute(transactionStatus -> {
            User vlad = new User()
                .setEmail("vlad@acme.com")
                .setFirstName("Vlad")
                .setLastName("Mihalcea");
            User john = new User()
                .setEmail("john@acme.com")
                .setFirstName("John")
                .setLastName("Doe");
            User jane = new User()
                .setEmail("jane@acme.com")
                .setFirstName("Jane")
                .setLastName("Doe");
            entityManager.persist(vlad);
            entityManager.persist(john);
            entityManager.persist(jane);

            PostComment johnReply = new PostComment()
                .setContent(JOHN_REPLY)
                .setAuthor(john);
            PostComment janeReply = new PostComment()
                .setContent(JANE_REPLY)
                .setAuthor(jane)
                .setParent(johnReply);

            Post post = new Post()
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("Optimistic locking at the aggregate root level")
                .setAuthor(vlad)
                .addComment(johnReply)
                .addComment(janeReply)
                .addTag(new Tag().setName("Spring"));
            entityManager.persist(post);

            return post.getId();
        });
    }

    @Test
    public void testStaleUpdateCannotRevertAnotherChild() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);
        assertEquals(0, alicePost.getVersion());

        LOGGER.info("Alice changes the first comment only");
        alicePost.getReplies().get(0).setContent("Alice's edit");
        assertEquals(1, forumService.updatePost(alicePost).getVersion());

        LOGGER.info("Bob changes the second comment, but his DTO still has the old first comment");
        bobPost.getReplies().get(1).setContent("Bob's edit");
        assertThrows(
            OptimisticLockingFailureException.class,
            () -> forumService.updatePost(bobPost)
        );

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals("Alice's edit", post.getReplies().get(0).getContent());
        assertEquals(JANE_REPLY, post.getReplies().get(1).getContent());
    }

    @Test
    public void testConcurrentUpdateTransactions() {
        PostDto originalPost = forumService.loadPostDTO(postId);

        PostDto alicePost = JsonUtil.clone(originalPost);
        alicePost.getReplies().get(0).setContent("Alice's edit");

        PostDto bobPost = JsonUtil.clone(originalPost);
        bobPost.getReplies().get(1).setContent("Bob's edit");

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            assertThrows(
                OptimisticLockingFailureException.class,
                () -> transactionTemplate.executeWithoutResult(transactionStatus -> {
                    LOGGER.info("Bob passes the version check and merges his changes");
                    forumService.updatePost(bobPost);

                    LOGGER.info("Alice passes the same version check and commits first");
                    try {
                        executorService.submit(() -> forumService.updatePost(alicePost))
                            .get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }

                    LOGGER.info("Bob's flush finds that the post version has changed");
                })
            );
        } finally {
            executorService.shutdownNow();
        }

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(1, post.getVersion());
        assertEquals("Alice's edit", post.getReplies().get(0).getContent());
        assertEquals(JANE_REPLY, post.getReplies().get(1).getContent());
    }
}
