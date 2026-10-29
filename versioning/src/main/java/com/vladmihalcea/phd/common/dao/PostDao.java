package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.PostDto;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import tools.jackson.databind.JsonNode;

/**
 * @author Vlad Mihalcea
 */
public interface PostDao {
    PostDto findById(Long id);

    PostDto findByIdUsingMultiset(Long postId);

    JsonNode findByIdAsJsonNodeUsingMultiset(Long postId);

    String findByIdAsJsonStringUsingMultiset(Long postId);

    void insert(PostDto post);

    /**
     * Updates the post basic attributes and increments the post version,
     * provided that the post still has the version of the given DTO.
     *
     * @return the new post version
     * @throws OptimisticLockingFailureException if the post version has changed
     */
    short update(PostDto post);

    /**
     * Increments the post version, provided that the post still has the given version.
     * Any write to the Post aggregate must increment the root version, so that
     * concurrent writes to different child rows still conflict on the post row.
     *
     * @return the new post version
     * @throws OptimisticLockingFailureException if the post version has changed
     */
    short incrementVersion(Long postId, short version);

    void delete(Long id);

    void linkTag(Long postId, Long tagId);

    void unlinkTag(Long postId, Long tagId);

    /**
     * Fails the write if the version predicate matched no post row.
     */
    static short checkVersion(int updateCount, Long postId, short nextVersion) {
        if (updateCount == 0) {
            throw new ObjectOptimisticLockingFailureException(PostDto.class, postId);
        }
        return nextVersion;
    }
}
