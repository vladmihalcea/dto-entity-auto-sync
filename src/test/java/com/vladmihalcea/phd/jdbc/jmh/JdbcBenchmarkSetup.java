package com.vladmihalcea.phd.jdbc.jmh;

import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.UserDao;
import com.vladmihalcea.phd.common.dto.*;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.common.util.TimeSortedIdentifierGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Vlad Mihalcea
 */
@Component
public class JdbcBenchmarkSetup {

    @Autowired
    private PostDao postDAO;

    @Autowired
    private ForumService forumService;

    @Autowired
    private UserDao userDAO;

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

            transactionTemplate.execute(status -> {
                userDAO.insert(vlad);
                userDAO.insert(john);
                userDAO.insert(jane);
                return null;
            });

            UserContext.setCurrentUser(vlad);

            PostDto post = new PostDto()
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