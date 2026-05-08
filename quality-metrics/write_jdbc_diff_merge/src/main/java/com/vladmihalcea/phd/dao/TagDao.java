package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.TagDto;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
public interface TagDao {
    List<TagDto> findByPostId(Long postId);

    void upsert(TagDto tag);
}
