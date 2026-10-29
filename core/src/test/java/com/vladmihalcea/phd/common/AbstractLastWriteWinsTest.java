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
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Documents the behavior of the base Diff-Merge pattern under concurrent writes.
 * Since the client sends the snapshot it has read, a stale write overwrites only
 * the rows it changes, and the last write wins without either client being notified.
 * The versioning module rejects such stale writes instead.
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractLastWriteWinsTest extends AbstractSpringTest {

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

    private Long postId;

    @Override
    protected void deleteAll() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DELETE FROM post_tags");
        jdbcTemplate.execute("DELETE FROM post_comments");
        jdbcTemplate.execute("DELETE FROM posts");
        jdbcTemplate.execute("DELETE FROM tags");
        jdbcTemplate.execute("DELETE FROM users");
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
                .setContent("Concurrent writes without optimistic locking")
                .setAuthor(vlad)
                .setReplies(List.of())
                .setTags(new ArrayList<>());
            postDAO.insert(post);

            PostCommentDto johnReply = new PostCommentDto()
                .setId(TimeSortedIdentifierGenerator.generate())
                .setContent("Interesting topic, but I would like to see more examples")
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
    public void testStaleDiffMergeOfAnotherChildKeepsBothChanges() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);

        LOGGER.info("Alice changes the first comment");
        PostDto aliceModifiedPost = JsonUtil.clone(alicePost);
        aliceModifiedPost.getReplies().get(0).setContent("Alice's edit");
        forumService.diffMerge(alicePost, aliceModifiedPost);

        LOGGER.info("Bob changes the second comment, based on the snapshot he read before Alice's change");
        PostDto bobModifiedPost = JsonUtil.clone(bobPost);
        bobModifiedPost.getReplies().get(1).setContent("Bob's edit");
        forumService.diffMerge(bobPost, bobModifiedPost);

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals("Alice's edit", post.getReplies().get(0).getContent());
        assertEquals("Bob's edit", post.getReplies().get(1).getContent());
    }

    @Test
    public void testStaleDiffMergeOfSameChildOverwritesTheFirstChange() {
        PostDto alicePost = forumService.loadPostDTO(postId);
        PostDto bobPost = forumService.loadPostDTO(postId);

        LOGGER.info("Alice changes the second comment");
        PostDto aliceModifiedPost = JsonUtil.clone(alicePost);
        aliceModifiedPost.getReplies().get(1).setContent("Alice's edit");
        forumService.diffMerge(alicePost, aliceModifiedPost);

        LOGGER.info("Bob changes the same comment, without knowing about Alice's change");
        PostDto bobModifiedPost = JsonUtil.clone(bobPost);
        bobModifiedPost.getReplies().get(1).setContent("Bob's edit");
        forumService.diffMerge(bobPost, bobModifiedPost);

        PostDto post = forumService.loadPostDTO(postId);
        assertEquals("Bob's edit", post.getReplies().get(1).getContent());
    }
}
