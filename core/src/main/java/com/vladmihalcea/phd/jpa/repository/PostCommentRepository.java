package com.vladmihalcea.phd.jpa.repository;

import com.vladmihalcea.phd.jpa.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /**
     * Finds a comment only within the given post, so that a Multi-Ops write cannot change the comment of another post.
     */
    Optional<PostComment> findByIdAndPostId(Long id, Long postId);
}
