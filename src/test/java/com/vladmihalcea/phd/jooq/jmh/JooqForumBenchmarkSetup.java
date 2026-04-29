package com.vladmihalcea.phd.jooq.jmh;

import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import com.vladmihalcea.phd.jooq.dao.PostDAO;
import com.vladmihalcea.phd.jooq.dao.UserDAO;
import com.vladmihalcea.phd.jooq.service.ForumService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring-managed helper used by jOOQ JMH benchmarks to create and clean up
 * the standard forum dataset between benchmark invocations.
 *
 * <p>Uses a monotonic {@link AtomicLong} counter for entity IDs to avoid the
 * TSID same-millisecond collision window that occurs when a new factory is
 * built for each {@code TimeSortedIdentifierGenerator.generate()} call.
 *
 * <p>The child comment is given a lower ID than the parent so that
 * {@code ORDER BY id} in the DAO places the child first — matching the
 * ordering the benchmark logic relies on ({@code replies.removeFirst()} must
 * remove the child, not the parent).
 *
 * @author Vlad Mihalcea
 */
@Component
public class JooqForumBenchmarkSetup {

    @Autowired
    private PostDAO postDAO;

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DSLContext ctx;

    /**
     * Creates the standard forum fixture (3 users, 1 post, 2 comments, 4 tags)
     * and stores the current user in {@link UserContext}.
     *
     * @return the id of the persisted post
     */
    public Long createTestData() {
        return transactionTemplate.<Long>execute(transactionStatus -> {
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

            postDAO.insert(post);
            Long postId = post.getId();

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
            return post.getId();
        });
    }

    /** Deletes all rows in dependency order inside a single committed transaction. */
    public void deleteAll() {
        transactionTemplate.execute(status -> {
            ctx.execute("DELETE FROM post_tag");
            ctx.execute("DELETE FROM post_comments");
            ctx.execute("DELETE FROM posts");
            ctx.execute("DELETE FROM tags");
            ctx.execute("DELETE FROM users");
            return null;
        });
    }
}