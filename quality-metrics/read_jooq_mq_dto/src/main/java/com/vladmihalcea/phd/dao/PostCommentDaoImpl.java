package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.UserDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vladmihalcea.phd.dao.schema.Tables.*;
/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostCommentDaoImpl implements PostCommentDao {

    private final DSLContext ctx;

    public PostCommentDaoImpl(DSLContext ctx) {
        this.ctx = ctx;
    }

    public List<PostCommentDto> findByPostId(Long postId) {
        return ctx.select(
                PC_ID, PC_CONTENT, PC_VOTES, PC_PARENT_ID,
                USERS_ID.as("author_id"), USERS_EMAIL, USERS_FIRST_NAME, USERS_LAST_NAME
            )
            .from(POST_COMMENTS)
            .join(USERS).on(USERS_ID.eq(PC_AUTHOR_ID))
            .where(PC_POST_ID.eq(postId))
            .orderBy(PC_ID)
            .fetch(r -> {
                UserDto author = new UserDto()
                    .setId(r.get("author_id", Long.class))
                    .setEmail(r.get(USERS_EMAIL))
                    .setFirstName(r.get(USERS_FIRST_NAME))
                    .setLastName(r.get(USERS_LAST_NAME));
                return new PostCommentDto()
                    .setId(r.get(PC_ID))
                    .setContent(r.get(PC_CONTENT))
                    .setVotes(r.get(PC_VOTES))
                    .setAuthor(author)
                    .setParentId(r.get(PC_PARENT_ID));
            });
    }
}
