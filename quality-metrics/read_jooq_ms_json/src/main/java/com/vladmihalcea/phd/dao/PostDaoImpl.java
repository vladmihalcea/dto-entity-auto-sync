package com.vladmihalcea.phd.dao;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.springframework.stereotype.Repository;

import static com.vladmihalcea.phd.dao.schema.Tables.*;
import static org.jooq.impl.DSL.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostDaoImpl implements PostDao {

    private final DSLContext ctx;

    public PostDaoImpl(DSLContext ctx) {
        this.ctx = ctx;
    }

    public String findByIdAsJsonStringUsingMultiset(Long postId) {
        Field<JSONB> commentAuthorJson = jsonbObject(
            key("id").value(field(name("cu", "id"), Long.class)),
            key("email").value(field(name("cu", "email"), String.class)),
            key("firstName").value(field(name("cu", "first_name"), String.class)),
            key("lastName").value(field(name("cu", "last_name"), String.class))
        );

        Field<JSONB> repliesJson = field(
            select(coalesce(
                jsonbArrayAgg(jsonbObject(
                    key("id").value(PC_ID),
                    key("content").value(PC_CONTENT),
                    key("votes").value(PC_VOTES),
                    key("parentId").value(PC_PARENT_ID),
                    key("author").value(commentAuthorJson)
                )).orderBy(PC_ID),
                field("'[]'::jsonb", JSONB.class)
            ))
                .from(POST_COMMENTS)
                .join(USERS.as("cu")).on(field(name("cu", "id"), Long.class).eq(PC_AUTHOR_ID))
                .where(PC_POST_ID.eq(POSTS_ID))
        );

        Field<JSONB> tagsJson = field(
            select(coalesce(
                jsonbArrayAgg(jsonbObject(
                    key("id").value(TAGS_ID),
                    key("name").value(TAGS_NAME)
                )).orderBy(TAGS_ID),
                field("'[]'::jsonb", JSONB.class)
            ))
                .from(TAGS)
                .join(POST_TAGS).on(PT_POST_ID.eq(POSTS_ID))
                .where(PT_TAG_ID.eq(TAGS_ID))
        );

        JSONB jsonb = ctx.select(
                jsonbObject(
                    key("id").value(POSTS_ID),
                    key("title").value(POSTS_TITLE),
                    key("content").value(POSTS_CONTENT),
                    key("votes").value(POSTS_VOTES),
                    key("author").value(jsonbObject(
                        key("id").value(USERS_ID),
                        key("email").value(USERS_EMAIL),
                        key("firstName").value(USERS_FIRST_NAME),
                        key("lastName").value(USERS_LAST_NAME)
                    )),
                    key("replies").value(repliesJson),
                    key("tags").value(tagsJson)
                )
            )
            .from(POSTS)
            .join(USERS).on(USERS_ID.eq(POSTS_AUTHOR_ID))
            .where(POSTS_ID.eq(postId))
            .fetchOne(Record1::value1);

        return jsonb.data();
    }
}

