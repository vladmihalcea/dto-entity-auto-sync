package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.service.ForumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Documents the lost update of the base Diff-Merge pattern on JPA.
 * Since the service diffs the incoming DTO against the aggregate it re-reads,
 * a stale DTO silently reverts the changes of a concurrent write, even on the rows
 * that the stale client did not modify. The versioning module rejects such stale writes instead.
 *
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JpaConfiguration.class)
public class JpaLastWriteWinsTest extends AbstractSpringTest {

    private static final String JOHN_REPLY = "Interesting topic, but I would like to see more examples";

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
                .setContent("Can you apply this pattern to other platforms?")
                .setAuthor(jane)
                .setParent(johnReply);

            Post post = new Post()
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("Concurrent writes without optimistic locking")
                .setAuthor(vlad)
                .addComment(johnReply)
                .addComment(janeReply)
                .addTag(new Tag().setName("Spring"));
            entityManager.persist(post);

            return post.getId();
        });
    }

    @Test
    public void testStaleUpdateRevertsAnotherChild() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);

        LOGGER.info("Alice changes the first comment");
        alicePost.getReplies().get(0).setContent("Alice's edit");
        forumService.updatePost(alicePost);

        LOGGER.info("Bob changes the second comment, but his DTO still has the old first comment");
        bobPost.getReplies().get(1).setContent("Bob's edit");
        forumService.updatePost(bobPost);

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals(JOHN_REPLY, post.getReplies().get(0).getContent());
        assertEquals("Bob's edit", post.getReplies().get(1).getContent());
    }
}
