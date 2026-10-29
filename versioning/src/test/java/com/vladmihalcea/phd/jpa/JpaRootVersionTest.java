package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.util.AbstractSpringTest;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.service.ForumService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a write transaction increments the post version exactly once
 * when the Post aggregate changes, and leaves it unchanged otherwise.
 *
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JpaConfiguration.class)
public class JpaRootVersionTest extends AbstractSpringTest {

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
            entityManager.persist(vlad);
            entityManager.persist(john);

            PostComment johnReply = new PostComment()
                .setContent("Interesting topic, but I would like to see more examples")
                .setAuthor(john);
            PostComment vladReply = new PostComment()
                .setContent("Thanks, I will add more examples")
                .setAuthor(vlad)
                .setParent(johnReply);

            Post post = new Post()
                .setTitle("DTO to Entity Auto-Sync")
                .setContent("Optimistic locking at the aggregate root level")
                .setAuthor(vlad)
                .addComment(johnReply)
                .addComment(vladReply)
                .addTag(new Tag().setName("Spring"))
                .addTag(new Tag().setName("JPA"));
            entityManager.persist(post);

            return post.getId();
        });
    }

    @Test
    public void testNoChange() {
        assertVersionIncrement(0, post -> {});
    }

    @Test
    public void testPostChange() {
        assertVersionIncrement(1, post -> post.setTitle("Root-level optimistic locking"));
    }

    @Test
    public void testCommentUpdate() {
        assertVersionIncrement(1, post -> post.getReplies().getFirst().setContent("Edited"));
    }

    @Test
    public void testCommentInsert() {
        assertVersionIncrement(1, post -> post.getReplies().add(newReply(post)));
    }

    @Test
    public void testCommentRemoval() {
        assertVersionIncrement(1, post -> post.getReplies().removeLast());
    }

    @Test
    public void testTagRemoval() {
        assertVersionIncrement(1, post -> post.getTags().removeLast());
    }

    @Test
    public void testTagInsert() {
        assertVersionIncrement(1, post -> post.getTags().add(
            new TagDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setName("Hibernate")
        ));
    }

    @Test
    public void testPostChangeAndCommentUpdate() {
        assertVersionIncrement(1, post -> {
            post.setTitle("Root-level optimistic locking");
            post.getReplies().getFirst().setContent("Edited");
        });
    }

    @Test
    public void testBenchmarkWorkload() {
        assertVersionIncrement(1, post -> {
            post.setTitle("DTO to Entity Auto-Sync Pattern").upvote();
            post.getReplies().removeIf(comment -> comment.getParentId() != null);
            post.getReplies().forEach(PostCommentDto::upvote);
            post.getReplies().add(newReply(post));
            post.getTags().removeIf(tag -> tag.getName().equals("JPA"));
            post.getTags().add(
                new TagDto()
                    .setId(TimeSortedIdentifierGenerator.generate())
                    .setName("Jakarta Persistence")
            );
        });
    }

    private PostCommentDto newReply(PostDto post) {
        return new PostCommentDto()
            .setId(TimeSortedIdentifierGenerator.generate())
            .setContent("Does it work with Entity Framework?")
            .setAuthor(post.getAuthor())
            .setParentId(post.getReplies().getFirst().getId());
    }

    private void assertVersionIncrement(int expectedIncrement, Consumer<PostDto> change) {
        PostDto post = forumService.loadPostDTO(postId);
        short version = post.getVersion();

        change.accept(post);
        short newVersion = forumService.updatePost(post).getVersion();

        assertEquals(version + expectedIncrement, newVersion);
        assertEquals(newVersion, forumService.loadPostDTO(postId).getVersion());
    }
}
