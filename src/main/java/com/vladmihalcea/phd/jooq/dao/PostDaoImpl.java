package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import com.vladmihalcea.phd.common.util.JsonUtil;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;
import static org.jooq.impl.DSL.*;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.multiset;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;

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

    public PostDto findByIdUsingMultiset(Long postId) {
        return ctx.select(
                POSTS_ID,
                POSTS_TITLE,
                POSTS_CONTENT,
                POSTS_VOTES,
                USERS_ID.as("author_id"),
                USERS_EMAIL,
                USERS_FIRST_NAME,
                USERS_LAST_NAME,
                multiset(
                    select(
                        PC_ID,
                        PC_CONTENT,
                        PC_VOTES,
                        PC_PARENT_ID,
                        field(name("cu", "id"), Long.class).as("comment_author_id"),
                        field(name("cu", "email"), String.class).as("comment_author_email"),
                        field(name("cu", "first_name"), String.class).as("comment_author_first_name"),
                        field(name("cu", "last_name"), String.class).as("comment_author_last_name")
                    )
                        .from(POST_COMMENTS)
                        .join(USERS.as("cu")).on(
                            field(name("cu", "id"), Long.class).eq(PC_AUTHOR_ID)
                        )
                        .where(PC_POST_ID.eq(POSTS_ID))
                        .orderBy(PC_ID)
                ).convertFrom(r -> r.map(cr -> {
                    UserDto commentAuthor = new UserDto()
                        .setId(cr.get("comment_author_id", Long.class))
                        .setEmail(cr.get("comment_author_email", String.class))
                        .setFirstName(cr.get("comment_author_first_name", String.class))
                        .setLastName(cr.get("comment_author_last_name", String.class));
                    return new PostCommentDto()
                        .setId(cr.get(PC_ID))
                        .setContent(cr.get(PC_CONTENT))
                        .setVotes(cr.get(PC_VOTES))
                        .setParentId(cr.get(PC_PARENT_ID))
                        .setAuthor(commentAuthor);
                })).as("comments"),
                multiset(
                    select(TAGS_ID, TAGS_NAME)
                        .from(TAGS)
                        .join(POST_TAGS).on(PT_POST_ID.eq(TAGS_ID))
                        .where(PT_TAG_ID.eq(POSTS_ID))
                        .orderBy(TAGS_ID)
                ).convertFrom(r -> r.map(tr -> new TagDto()
                    .setId(tr.get(TAGS_ID))
                    .setName(tr.get(TAGS_NAME))
                )).as("tags")
            )
            .from(POSTS)
            .join(USERS).on(USERS_ID.eq(POSTS_AUTHOR_ID))
            .where(POSTS_ID.eq(postId))
            .fetchOne(r -> {
                UserDto author = new UserDto()
                    .setId(r.get("author_id", Long.class))
                    .setEmail(r.get(USERS_EMAIL))
                    .setFirstName(r.get(USERS_FIRST_NAME))
                    .setLastName(r.get(USERS_LAST_NAME));

                List<PostCommentDto> replies = r.value9();
                List<TagDto> tagList = r.value10();

                LinkedHashSet<TagDto> tags = new LinkedHashSet<>(tagList);

                return new PostDto()
                    .setId(r.get(POSTS_ID))
                    .setTitle(r.get(POSTS_TITLE))
                    .setContent(r.get(POSTS_CONTENT))
                    .setVotes(r.get(POSTS_VOTES))
                    .setAuthor(author)
                    .setReplies(replies)
                    .setTags(tags);
            });
    }

    public JsonNode findByIdAsJsonNodeUsingMultiset(Long postId) {
        return JsonUtil.JSON_MAPPER.readTree(
            findByIdAsJsonStringUsingMultiset(postId)
        );
    }

    public String findByIdAsJsonStringUsingMultiset(Long postId) {
        return findByIdAsJsonBinaryUsingMultiset(postId).data();
    }

    private JSONB findByIdAsJsonBinaryUsingMultiset(Long postId) {
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
                .join(POST_TAGS).on(PT_POST_ID.eq(TAGS_ID))
                .where(PT_TAG_ID.eq(POSTS_ID))
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
            .fetchOne(r -> r.value1());

        return jsonb;
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
        ctx.insertInto(POST_TAGS)
            .set(PT_POST_ID, tagId)
            .set(PT_TAG_ID, postId)
            .execute();
    }

    public void unlinkTag(Long postId, Long tagId) {
        ctx.deleteFrom(POST_TAGS)
            .where(PT_POST_ID.eq(tagId).and(PT_TAG_ID.eq(postId)))
            .execute();
    }
}

