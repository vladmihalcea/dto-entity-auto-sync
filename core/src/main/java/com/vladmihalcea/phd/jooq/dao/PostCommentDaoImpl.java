package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;
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

    public void insert(Long postId, PostCommentDto comment) {
        ctx.insertInto(POST_COMMENTS)
            .set(PC_ID, comment.getId())
            .set(PC_CONTENT, comment.getContent())
            .set(PC_VOTES, comment.getVotes())
            .set(PC_POST_ID, postId)
            .set(PC_AUTHOR_ID, comment.getAuthor().getId())
            .set(PC_PARENT_ID, comment.getParentId())
            .execute();
    }

    public void update(Long postId, PostCommentDto comment) {
        ctx.update(POST_COMMENTS)
            .set(PC_CONTENT, comment.getContent())
            .set(PC_VOTES, comment.getVotes())
            .set(PC_PARENT_ID, comment.getParentId())
            .where(PC_ID.eq(comment.getId()).and(PC_POST_ID.eq(postId)))
            .execute();
    }

    public void delete(Long postId, Long id) {
        ctx.deleteFrom(POST_COMMENTS)
            .where(PC_ID.eq(id).and(PC_POST_ID.eq(postId)))
            .execute();
    }

    /**
     * Recursively deletes all descendants of the given parent comment using a
     * PostgreSQL recursive CTE — no DDL ON DELETE CASCADE required.
     */
    public void deleteChildrenOf(Long postId, Long parentId) {
        Field<Long> id = DSL.field(DSL.name("descendants", "id"), Long.class);

        CommonTableExpression<org.jooq.Record1<Long>> descendants = DSL.name("descendants")
            .fields("id")
            .as(
                DSL.select(PC_ID)
                    .from(POST_COMMENTS)
                    .where(PC_PARENT_ID.eq(parentId).and(PC_POST_ID.eq(postId)))
                    .unionAll(
                        DSL.select(PC_ID)
                            .from(POST_COMMENTS)
                            .join(DSL.table(DSL.name("descendants")))
                            .on(PC_PARENT_ID.eq(
                                DSL.field(DSL.name("descendants", "id"), Long.class)))
                    )
            );

        ctx.deleteFrom(POST_COMMENTS)
            .where(PC_ID.in(
                ctx.withRecursive(descendants)
                    .select(id)
                    .from(DSL.table(DSL.name("descendants")))
            ))
            .execute();
    }
}
