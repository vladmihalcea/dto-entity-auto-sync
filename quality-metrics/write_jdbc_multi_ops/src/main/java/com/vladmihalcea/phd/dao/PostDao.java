package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostDto;

/**
 * @author Vlad Mihalcea
 */
public interface PostDao {
    PostDto findById(Long id);

    void update(PostDto post);

    void linkTag(Long postId, Long tagId);

    void unlinkTag(Long postId, Long tagId);
}
