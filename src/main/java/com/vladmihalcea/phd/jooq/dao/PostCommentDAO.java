package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dao.AbstractPostCommentDAO;
import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.UserDTO;
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
public class PostCommentDAO implements AbstractPostCommentDAO {

    private final DSLContext ctx;

    public PostCommentDAO(DSLContext ctx) {
        this.ctx = ctx;
    }

    public List<PostCommentDTO> findByPostId(Long postId) {
        return ctx.select(
                PC_ID, PC_CONTENT, PC_VOTES, PC_PARENT_ID,
                USERS_ID.as("author_id"), USERS_EMAIL, USERS_FIRST_NAME, USERS_LAST_NAME
            )
            .from(POST_COMMENTS)
            .join(USERS).on(USERS_ID.eq(PC_AUTHOR_ID))
            .where(PC_POST_ID.eq(postId))
            .orderBy(PC_ID)
            .fetch(r -> {
                UserDTO author = new UserDTO()
                    .setId(r.get("author_id", Long.class))
                    .setEmail(r.get(USERS_EMAIL))
                    .setFirstName(r.get(USERS_FIRST_NAME))
                    .setLastName(r.get(USERS_LAST_NAME));
                return new PostCommentDTO()
                    .setId(r.get(PC_ID))
                    .setContent(r.get(PC_CONTENT))
                    .setVotes(r.get(PC_VOTES))
                    .setAuthor(author)
                    .setParentId(r.get(PC_PARENT_ID));
            });
    }

    public void insert(Long postId, PostCommentDTO comment) {
        ctx.insertInto(POST_COMMENTS)
            .set(PC_ID, comment.getId())
            .set(PC_CONTENT, comment.getContent())
            .set(PC_VOTES, comment.getVotes())
            .set(PC_POST_ID, postId)
            .set(PC_AUTHOR_ID, comment.getAuthor().getId())
            .set(PC_PARENT_ID, comment.getParentId())
            .execute();
    }

    public void update(PostCommentDTO comment) {
        ctx.update(POST_COMMENTS)
            .set(PC_CONTENT, comment.getContent())
            .set(PC_VOTES, comment.getVotes())
            .set(PC_PARENT_ID, comment.getParentId())
            .where(PC_ID.eq(comment.getId()))
            .execute();
    }

    public void delete(Long id) {
        ctx.deleteFrom(POST_COMMENTS).where(PC_ID.eq(id)).execute();
    }

    /**
     * Recursively deletes all descendants of the given parent comment using a
     * PostgreSQL recursive CTE — no DDL ON DELETE CASCADE required.
     */
    public void deleteChildrenOf(Long parentId) {
        Field<Long> id = DSL.field(DSL.name("descendants", "id"), Long.class);

        CommonTableExpression<org.jooq.Record1<Long>> descendants = DSL.name("descendants")
            .fields("id")
            .as(
                DSL.select(PC_ID)
                    .from(POST_COMMENTS)
                    .where(PC_PARENT_ID.eq(parentId))
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
