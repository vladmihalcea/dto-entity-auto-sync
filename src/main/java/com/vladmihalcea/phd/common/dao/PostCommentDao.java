package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostCommentDto;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
public interface PostCommentDao {

    List<PostCommentDto> findByPostId(Long postId);

    void insert(Long postId, PostCommentDto comment);

    void update(PostCommentDto comment);

    void delete(Long id);

    void deleteChildrenOf(Long parentId);
}
