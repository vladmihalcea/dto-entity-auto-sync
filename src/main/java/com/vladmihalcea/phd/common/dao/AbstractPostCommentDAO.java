package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
public interface AbstractPostCommentDAO {

    List<PostCommentDTO> findByPostId(Long postId);

    void insert(Long postId, PostCommentDTO comment);

    void update(PostCommentDTO comment);

    void delete(Long id);

    void deleteChildrenOf(Long parentId);
}
