package com.vladmihalcea.phd.jooq.dao;

import com.vladmihalcea.phd.common.dao.TagDao;
import com.vladmihalcea.phd.common.dto.TagDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vladmihalcea.phd.jooq.schema.Tables.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class TagDaoImpl implements TagDao {

    private final DSLContext ctx;

    public TagDaoImpl(DSLContext ctx) {
        this.ctx = ctx;
    }

    public TagDto findByName(String name) {
        return ctx.select(TAGS_ID, TAGS_NAME, TAGS_DESCRIPTION)
            .from(TAGS)
            .where(TAGS_NAME.eq(name))
            .fetchOne(r -> new TagDto()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public List<TagDto> findByPostId(Long postId) {
        return ctx.select(TAGS_ID, TAGS_NAME, TAGS_DESCRIPTION)
            .from(TAGS)
            .join(POST_TAGS).on(PT_TAG_ID.eq(TAGS_ID))
            .where(PT_POST_ID.eq(postId))
            .orderBy(TAGS_ID)
            .fetch(r -> new TagDto()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public void insert(TagDto tag) {
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
    public TagDto upsert(TagDto tag) {
        return ctx.insertInto(TAGS)
            .set(TAGS_ID, tag.getId())
            .set(TAGS_NAME, tag.getName())
            .onConflictDoNothing()
            .returning(TAGS_ID, TAGS_NAME)
            .fetchOne(r -> new TagDto()
                .setId(r.get(TAGS_ID))
                .setName(r.get(TAGS_NAME)));
    }

    public void delete(Long id) {
        ctx.deleteFrom(TAGS).where(TAGS_ID.eq(id)).execute();
    }
}

