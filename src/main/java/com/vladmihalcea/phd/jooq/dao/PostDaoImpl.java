package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostDaoImpl implements PostDao {

    private final DSLContext ctx;

    public PostDaoImpl(DSLContext ctx) {
        this.ctx = ctx;
    }

    public PostDto findById(Long id) {
        return ctx.select(
                POSTS_ID, POSTS_TITLE, POSTS_CONTENT, POSTS_VOTES,
                USERS_ID.as("author_id"), USERS_EMAIL, USERS_FIRST_NAME, USERS_LAST_NAME
            )
            .from(POSTS)
            .join(USERS).on(USERS_ID.eq(POSTS_AUTHOR_ID))
            .where(POSTS_ID.eq(id))
            .fetchOne(r -> {
                UserDto author = new UserDto()
                    .setId(r.get("author_id", Long.class))
                    .setEmail(r.get(USERS_EMAIL))
                    .setFirstName(r.get(USERS_FIRST_NAME))
                    .setLastName(r.get(USERS_LAST_NAME));
                return new PostDto()
                    .setId(r.get(POSTS_ID))
                    .setTitle(r.get(POSTS_TITLE))
                    .setContent(r.get(POSTS_CONTENT))
                    .setVotes(r.get(POSTS_VOTES))
                    .setAuthor(author);
            });
    }

    public void insert(PostDto post) {
        ctx.insertInto(POSTS)
            .set(POSTS_ID, post.getId())
            .set(POSTS_TITLE, post.getTitle())
            .set(POSTS_CONTENT, post.getContent())
            .set(POSTS_VOTES, post.getVotes())
            .set(POSTS_AUTHOR_ID, post.getAuthor().getId())
            .execute();
    }

    public void update(PostDto post) {
        ctx.update(POSTS)
            .set(POSTS_TITLE, post.getTitle())
            .set(POSTS_CONTENT, post.getContent())
            .set(POSTS_VOTES, post.getVotes())
            .where(POSTS_ID.eq(post.getId()))
            .execute();
    }

    public void delete(Long id) {
        ctx.deleteFrom(POSTS).where(POSTS_ID.eq(id)).execute();
    }

    /**
     * Link a tag to a post.
     * Note: Tag owns the join table — post_id column references tags.id,
     * tag_id column references posts.id.
     */
    public void linkTag(Long postId, Long tagId) {
        ctx.insertInto(POST_TAG)
            .set(PT_POST_ID, tagId)
            .set(PT_TAG_ID, postId)
            .execute();
    }

    public void unlinkTag(Long postId, Long tagId) {
        ctx.deleteFrom(POST_TAG)
            .where(PT_POST_ID.eq(tagId).and(PT_TAG_ID.eq(postId)))
            .execute();
    }
}

