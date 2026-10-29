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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
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

    @Transactional
    public void updatePost(PostDto postDTO) {
        postMapper.merge(
            postDTO,
            loadPostEntityWithAllAssociations(postDTO.getId())
        );
        LOGGER.info("Auto-flush the JPA entity");
    }

    @Transactional
    public void updateDetachedPost(PostDto postDTO) {
        LOGGER.info("Convert DTO to JPA entity");
        Post postFromDatabase = loadPostEntityWithAllAssociations(postDTO.getId());
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
     */
    @Transactional
    public void patchPost(PostDto postDTO) {
        postRepository.findById(postDTO.getId()).orElseThrow()
            .setTitle(postDTO.getTitle())
            .setContent(postDTO.getContent())
            .setVotes(postDTO.getVotes());
    }

    @Transactional
    public PostCommentDto createPostComment(Long postId, PostCommentDto commentDTO) {
        entityManager.persist(
            postCommentMapper.toEntity(commentDTO)
                .setPost(postRepository.getReferenceById(postId))
        );
        return commentDTO;
    }

    @Transactional
    public void updatePostComment(Long postId, PostCommentDto commentDTO) {
        postCommentRepository.findByIdAndPostId(commentDTO.getId(), postId).orElseThrow()
            .setContent(commentDTO.getContent())
            .setVotes(commentDTO.getVotes())
            .setParent(
                commentDTO.getParentId() != null ?
                    entityManager.getReference(PostComment.class, commentDTO.getParentId()) :
                    null
            );
    }

    /**
     * Removes the comment and its replies through the comment collection of the post, as the Diff-Merge does.
     */
    @Transactional
    public void removePostComment(Long postId, Long commentId) {
        Post post = postRepository.findByIdWithComments(postId);
        post.removeComment(
            post.getComments().stream()
                .filter(comment -> comment.getId().equals(commentId))
                .findFirst()
                .orElseThrow()
        );
    }

    @Transactional
    public TagDto createTag(Long postId, TagDto tagDTO) {
        postRepository.findByIdWithTags(postId).addTag(tagMapper.toEntity(tagDTO));
        return tagDTO;
    }

    @Transactional
    public void removeTag(Long postId, TagDto tagDTO) {
        Post post = postRepository.findByIdWithTags(postId);
        post.removeTag(
            post.getTags().stream()
                .filter(tag -> tag.getId().equals(tagDTO.getId()))
                .findFirst()
                .orElseThrow()
        );
    }
}
