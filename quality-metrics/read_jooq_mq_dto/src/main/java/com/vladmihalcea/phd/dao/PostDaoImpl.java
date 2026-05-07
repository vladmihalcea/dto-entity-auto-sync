package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.UserDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import static com.vladmihalcea.phd.dao.schema.Tables.*;

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
}

