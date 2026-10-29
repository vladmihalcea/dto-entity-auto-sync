package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostCommentDto;

import java.util.List;

/**
 * The write methods are scoped to the given post, so that
 * a write to one post cannot modify the comments of another post.
 *
 * @author Vlad Mihalcea
 */
public interface PostCommentDao {

    List<PostCommentDto> findByPostId(Long postId);

    void insert(Long postId, PostCommentDto comment);

    void update(Long postId, PostCommentDto comment);

    void delete(Long postId, Long id);

    void deleteChildrenOf(Long postId, Long parentId);
}
