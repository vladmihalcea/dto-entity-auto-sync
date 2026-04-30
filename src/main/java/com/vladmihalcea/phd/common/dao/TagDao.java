package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.TagDto;

import java.util.List;

/**
 * The {@link TagDao}
 *
 * @author Vlad Mihalcea
 * @since 1.x.y
 */
public interface TagDao {
    TagDto findByName(String name);

    List<TagDto> findByPostId(Long postId);

    void insert(TagDto tag);

    void delete(Long id);

    TagDto upsert(TagDto tag);
}
