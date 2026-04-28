package com.vladmihalcea.phd.jdbc.multi_ops_sync.jmh;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.common.dto.UserDTO;
import com.vladmihalcea.phd.jdbc.multi_ops_sync.dao.UserDAO;
import com.vladmihalcea.phd.jdbc.multi_ops_sync.service.ForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spring-managed helper used by JDBC JMH benchmarks to create and clean up
 * the standard forum dataset between benchmark invocations.
 *
 * <p>Uses a monotonic {@link AtomicLong} counter for entity IDs to avoid the
 * TSID same-millisecond collision window that occurs when a new factory is
 * built for each {@code TimeSortedIdentifierGenerator.generate()} call.
 *
 * @author Vlad Mihalcea
 */
@Component
public class JdbcForumBenchmarkSetup {

    private static final AtomicLong ID_COUNTER =
        new AtomicLong(System.currentTimeMillis() * 1_000);

    private static long nextId() {
        return ID_COUNTER.getAndIncrement();
    }

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Creates the standard forum fixture (3 users, 1 post, 2 comments, 4 tags)
     * and stores the current user in {@link UserContext}.
     *
     * @return the id of the persisted post
     */
    public Long createTestData() {
        UserDTO vlad = new UserDTO().setId(nextId())
            .setEmail("vlad@acme.com")
            .setFirstName("Vlad")
            .setLastName("Mihalcea");
        UserDTO john = new UserDTO().setId(nextId())
            .setEmail("john@acme.com")
            .setFirstName("John")
            .setLastName("Doe");
        UserDTO jane = new UserDTO().setId(nextId())
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
            .setId(nextId())
            .setTitle("DTO to Entity Auto-Sync")
            .setContent("""
                    Spring and Hibernate can help you do the aut-sync
                    between DTOs and JPA entities
                    """)
            .setAuthor(vlad)
            .setReplies(List.of())
            .setTags(new LinkedHashSet<>());
        forumService.createPost(post);

        PostCommentDTO johnReply = new PostCommentDTO()
            .setId(nextId() + 1)          // parent gets the HIGHER id …
            .setContent("Interesting topic, but I would like to see more examples")
            .setAuthor(john);
        PostCommentDTO janeReply = new PostCommentDTO()
            .setId(johnReply.getId() - 1) // … child gets the LOWER id so ORDER BY id
            .setContent("Can you apply this pattern to other platforms?") //  puts the child first
            .setAuthor(jane)
            .setParentId(johnReply.getId());

        forumService.createPostComment(post.getId(), johnReply);
        forumService.createPostComment(post.getId(), janeReply);

        forumService.createTag(post.getId(), new TagDTO().setId(nextId()).setName("Spring"));
        forumService.createTag(post.getId(), new TagDTO().setId(nextId()).setName("JPA"));
        forumService.createTag(post.getId(), new TagDTO().setId(nextId()).setName("Hibernate"));
        forumService.createTag(post.getId(), new TagDTO().setId(nextId()).setName("MapStruct"));

        return post.getId();
    }

    /** Deletes all rows in dependency order inside a single committed transaction. */
    public void deleteAll() {
        transactionTemplate.execute(status -> {
            jdbcTemplate.execute("DELETE FROM post_tag");
            jdbcTemplate.execute("DELETE FROM post_comments");
            jdbcTemplate.execute("DELETE FROM posts");
            jdbcTemplate.execute("DELETE FROM tags");
            jdbcTemplate.execute("DELETE FROM users");
            return null;
        });
    }
}



