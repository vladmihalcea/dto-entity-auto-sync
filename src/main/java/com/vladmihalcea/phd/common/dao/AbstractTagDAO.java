package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.TagDTO;

import java.util.List;

/**
 * The {@link AbstractTagDAO}
 *
 * @author Vlad Mihalcea
 * @since 1.x.y
 */
public interface AbstractTagDAO {
    TagDTO findByName(String name);

    List<TagDTO> findByPostId(Long postId);

    void insert(TagDTO tag);

    void delete(Long id);

    TagDTO upsert(TagDTO tag);
}
