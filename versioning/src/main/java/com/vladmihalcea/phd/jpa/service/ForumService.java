package com.vladmihalcea.phd.jpa.service;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.mapper.PostCommentMapper;
import com.vladmihalcea.phd.jpa.mapper.PostMapper;
import com.vladmihalcea.phd.jpa.mapper.TagMapper;
import com.vladmihalcea.phd.jpa.repository.PostCommentRepository;
import com.vladmihalcea.phd.jpa.repository.PostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every write of the Post aggregate checks the version the client has read and returns the post,
 * whose version the caller reads after the transaction has committed.
 * Hibernate increments the root version when the commit flushes the post or its collections,
 * and the {@code RootAware} event listeners do so before completion when only a child entity changes,
 * so the new version is set only once the transactional proxy has committed, before it returns the post.
 * A write that joins an outer transaction returns the post before its version is incremented.
 *
 * @author Vlad Mihalcea
 */
@Service
public class ForumService {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Autowired
    private PostCommentMapper postCommentMapper;

    @Autowired
    private TagMapper tagMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PostDto loadPostDTO(Long id) {
        return postMapper.toDTO(
            loadPostEntityWithAllAssociations(id)
        );
    }

    @Transactional(readOnly = true)
    public com.vladmihalcea.phd.jpa.view.PostDto loadPostDTOUsingMultiset(Long id) {
        return postRepository.loadPostDTOUsingMultiset(id);
    }

    private Post loadPostEntityWithAllAssociations(Long id) {
        Post post = postRepository.findByIdWithComments(id);
        return postRepository.findByIdWithTags(post.getId());
    }

    /**
     * Hibernate ignores any change to the version of a managed entity,
     * so the version the client has read must be checked explicitly.
     */
    private static Post checkVersion(Post post, short version) {
        if (post.getVersion() != version) {
            throw new ObjectOptimisticLockingFailureException(Post.class, post.getId());
        }
        return post;
    }

    private Post loadPostEntityForUpdate(PostDto postDTO) {
        return checkVersion(loadPostEntityWithAllAssociations(postDTO.getId()), postDTO.getVersion());
    }

    /**
     * @return the post, with the new version of the Post aggregate
     */
    @Transactional
    public Post updatePost(PostDto postDTO) {
        Post postFromDatabase = loadPostEntityForUpdate(postDTO);
        postMapper.merge(postDTO, postFromDatabase);
        LOGGER.info("Auto-flush the JPA entity");
        return postFromDatabase;
    }

    @Transactional
    public void updateDetachedPost(PostDto postDTO) {
        LOGGER.info("Convert DTO to JPA entity");
        Post postFromDatabase = loadPostEntityForUpdate(postDTO);
        postMapper.merge(postDTO, postFromDatabase);
        LOGGER.info("Merge updated JPA entity");
        entityManager.detach(postFromDatabase);
        postRepository.save(postFromDatabase);
    }

    /**
     * Updates the post's own fields, the first call of the Multi-Ops variant (R1-14), which is not idiomatic on JPA,
     * since {@link #updatePost(PostDto)} merges the whole aggregate in one unit of work. Like the per-change endpoints
     * of JDBC and jOOQ, every Multi-Ops method changes one part of the aggregate in its own transaction, and every
     * child write is scoped to the given post.
     * <p>
     * Every Multi-Ops method loads what its {@code core} counterpart loads, checks the version the client has read
     * on the post, and returns the post. Where the {@code core} method only references the post, the version check
     * loads it.
     *
     * @return the post, with the new version of the Post aggregate
     */
    @Transactional
    public Post patchPost(PostDto postDTO) {
        return checkVersion(postRepository.findById(postDTO.getId()).orElseThrow(), postDTO.getVersion())
            .setTitle(postDTO.getTitle())
            .setContent(postDTO.getContent())
            .setVotes(postDTO.getVotes());
    }

    @Transactional
    public Post createPostComment(Long postId, short version, PostCommentDto commentDTO) {
        Post post = checkVersion(postRepository.getReferenceById(postId), version);
        entityManager.persist(
            postCommentMapper.toEntity(commentDTO)
                .setPost(post)
        );
        return post;
    }

    @Transactional
    public Post updatePostComment(Long postId, short version, PostCommentDto commentDTO) {
        PostComment comment = postCommentRepository.findByIdAndPostId(commentDTO.getId(), postId).orElseThrow();
        Post post = checkVersion(comment.getPost(), version);
        comment
            .setContent(commentDTO.getContent())
            .setVotes(commentDTO.getVotes())
            .setParent(
                commentDTO.getParentId() != null ?
                    entityManager.getReference(PostComment.class, commentDTO.getParentId()) :
                    null
            );
        return post;
    }

    /**
     * Removes the comment and its replies through the comment collection of the post, as the Diff-Merge does.
     */
    @Transactional
    public Post removePostComment(Long postId, short version, Long commentId) {
        Post post = checkVersion(postRepository.findByIdWithComments(postId), version);
        post.removeComment(
            post.getComments().stream()
                .filter(comment -> comment.getId().equals(commentId))
                .findFirst()
                .orElseThrow()
        );
        return post;
    }

    @Transactional
    public Post createTag(Long postId, short version, TagDto tagDTO) {
        return checkVersion(postRepository.findByIdWithTags(postId), version).addTag(tagMapper.toEntity(tagDTO));
    }

    @Transactional
    public Post removeTag(Long postId, short version, TagDto tagDTO) {
        Post post = checkVersion(postRepository.findByIdWithTags(postId), version);
        post.removeTag(
            post.getTags().stream()
                .filter(tag -> tag.getId().equals(tagDTO.getId()))
                .findFirst()
                .orElseThrow()
        );
        return post;
    }
}
