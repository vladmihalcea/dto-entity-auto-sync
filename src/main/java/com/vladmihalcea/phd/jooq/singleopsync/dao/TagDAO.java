package com.vladmihalcea.phd.jooq.singleopsync.dao;

import com.vladmihalcea.phd.common.dto.TagDTO;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vladmihalcea.phd.jooq.singleopsync.schema.Tables.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class TagDAO {

    private final DSLContext ctx;

    public TagDAO(DSLContext ctx) {
        this.ctx = ctx;
    }

    public TagDTO findByName(String name) {
        return ctx.select(TAGS_ID, TAGS_NAME, TAGS_DESCRIPTION)
            .from(TAGS)
            .where(TAGS_NAME.eq(name))
            .fetchOne(r -> new TagDTO()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public List<TagDTO> findByPostId(Long postId) {
        return ctx.select(TAGS_ID, TAGS_NAME, TAGS_DESCRIPTION)
            .from(TAGS)
            .join(POST_TAG).on(PT_POST_ID.eq(TAGS_ID))
            .where(PT_TAG_ID.eq(postId))
            .orderBy(TAGS_ID)
            .fetch(r -> new TagDTO()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public void insert(TagDTO tag) {
        ctx.insertInto(TAGS)
            .set(TAGS_ID, tag.getId())
            .set(TAGS_NAME, tag.getName())
            .execute();
    }

    /**
     * Insert the tag if it does not exist yet, or return the existing one.
     * Uses jOOQ {@code ON CONFLICT (name) DO UPDATE} so the returning clause
     * always provides the persisted id/name regardless of which branch was taken.
     */
    public TagDTO upsert(TagDTO tag) {
        return ctx.insertInto(TAGS)
            .set(TAGS_ID, tag.getId())
            .set(TAGS_NAME, tag.getName())
            .onConflictDoNothing()
            .returning(TAGS_ID, TAGS_NAME)
            .fetchOne(r -> new TagDTO()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public void delete(Long id) {
        ctx.deleteFrom(TAGS).where(TAGS_ID.eq(id)).execute();
    }
}

