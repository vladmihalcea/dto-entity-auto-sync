package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostDto;

/**
 * @author Vlad Mihalcea
 */
public interface PostDao {
    PostDto findById(Long id);

    void insert(PostDto post);

    void update(PostDto post);

    void delete(Long id);

    void linkTag(Long postId, Long tagId);

    void unlinkTag(Long postId, Long tagId);
}
