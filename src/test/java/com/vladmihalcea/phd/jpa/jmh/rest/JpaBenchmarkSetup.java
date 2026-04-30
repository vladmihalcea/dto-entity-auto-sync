package com.vladmihalcea.phd.jpa.jmh.rest;

import com.vladmihalcea.phd.common.dto.UserContext;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
import com.vladmihalcea.phd.jpa.domain.User;
import com.vladmihalcea.phd.jpa.dto.mapper.UserMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author Vlad Mihalcea
 */
@Component
public class JpaBenchmarkSetup {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private UserMapper userMapper;

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
        return transactionTemplate.<Long>execute(transactionStatus -> {
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
                    Spring and Hibernate can help you do the aut-sync
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

    /**
     * Deletes all rows in dependency order inside a single committed transaction.
     */
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







