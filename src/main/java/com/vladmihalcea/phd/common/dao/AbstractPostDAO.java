package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostDTO;

/**
 * @author Vlad Mihalcea
 */
public interface AbstractPostDAO {
    PostDTO findById(Long id);

    void insert(PostDTO post);

    void update(PostDTO post);

    void delete(Long id);

    void linkTag(Long postId, Long tagId);

    void unlinkTag(Long postId, Long tagId);
}
