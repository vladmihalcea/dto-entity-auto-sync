package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.TagDto;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
public interface TagDao {
    TagDto findByName(String name);

    List<TagDto> findByPostId(Long postId);

    void insert(TagDto tag);

    void delete(Long id);

    void upsert(TagDto tag);
}
