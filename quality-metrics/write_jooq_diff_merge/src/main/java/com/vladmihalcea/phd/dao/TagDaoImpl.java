package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.TagDto;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.vladmihalcea.phd.dao.schema.Tables.*;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class TagDaoImpl implements TagDao {

    private final DSLContext ctx;

    public TagDaoImpl(DSLContext ctx) {
        this.ctx = ctx;
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

    public void upsert(TagDto tag) {
        ctx.insertInto(TAGS)
            .set(TAGS_ID, tag.getId())
            .set(TAGS_NAME, tag.getName())
            .onConflictDoNothing()
            .execute();
    }
}

