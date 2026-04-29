package com.vladmihalcea.phd.jdbc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.jdbc.dao.PostCommentDAO;
import com.vladmihalcea.phd.jdbc.dao.PostDAO;
import com.vladmihalcea.phd.jdbc.dao.TagDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional
public class ForumService {

    /** Jackson 2 ObjectMapper used exclusively for JSON-Patch serialization. */
    private static final ObjectMapper PATCH_MAPPER = new ObjectMapper();

    private final PostDAO postDAO;
    private final PostCommentDAO postCommentDAO;
    private final TagDAO tagDAO;

    public ForumService(PostDAO postDAO, PostCommentDAO postCommentDAO, TagDAO tagDAO) {
        this.postDAO = postDAO;
        this.postCommentDAO = postCommentDAO;
        this.tagDAO = tagDAO;
    }

    @Transactional(readOnly = true)
    public PostDTO loadPostDTO(Long postId) {
        PostDTO post = postDAO.findById(postId);
        List<PostCommentDTO> replies = postCommentDAO.findByPostId(postId);
        post.setReplies(replies);
        post.setTags(new LinkedHashSet<>(tagDAO.findByPostId(postId)));
        return post;
    }

    public void updatePost(PostDTO post) {
        postDAO.update(post);
    }

    public PostCommentDTO createPostComment(Long postId, PostCommentDTO comment) {
        postCommentDAO.insert(postId, comment);
        return comment;
    }

    public void updatePostComment(PostCommentDTO comment) {
        postCommentDAO.update(comment);
    }

    public void removePostComment(Long commentId) {
        postCommentDAO.deleteChildrenOf(commentId);
        postCommentDAO.delete(commentId);
    }

    public TagDTO createTag(Long postId, TagDTO tag) {
        TagDTO existing = tagDAO.findByName(tag.getName());
        if (existing != null) {
            postDAO.linkTag(postId, existing.getId());
            return existing;
        } else {
            tagDAO.insert(tag);
            postDAO.linkTag(postId, tag.getId());
            return tag;
        }
    }

    public void removeTag(Long postId, TagDTO tag) {
        postDAO.unlinkTag(postId, tag.getId());
    }

    @Transactional
    public void diffMerge(PostDTO originalPost, PostDTO modifiedPost) {
        if (!Objects.equals(originalPost.getTitle(), modifiedPost.getTitle()) ||
            !Objects.equals(originalPost.getContent(), modifiedPost.getContent()) ||
            originalPost.getVotes() != modifiedPost.getVotes()) {
            postDAO.update(modifiedPost);
        }

        Map<Long, PostCommentDTO> originalComments = originalPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDTO::getId, c -> c));
        Map<Long, PostCommentDTO> modifiedComments = modifiedPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDTO::getId, c -> c));

        for (Map.Entry<Long, PostCommentDTO> entry : originalComments.entrySet()) {
            if (!modifiedComments.containsKey(entry.getKey())) {
                postCommentDAO.deleteChildrenOf(entry.getKey());
                postCommentDAO.delete(entry.getKey());
            }
        }

        // Insertions and updates
        for (Map.Entry<Long, PostCommentDTO> entry : modifiedComments.entrySet()) {
            Long id = entry.getKey();
            PostCommentDTO original = originalComments.get(id);
            PostCommentDTO modified = entry.getValue();
            if (original == null) {
                postCommentDAO.insert(originalPost.getId(), modified);
            } else if (!Objects.equals(original.getContent(), modified.getContent()) ||
                       original.getVotes() != modified.getVotes() ||
                       !Objects.equals(original.getParentId(), modified.getParentId())) {
                postCommentDAO.update(modified);
            }
        }

        Map<Long, TagDTO> originalTags = originalPost.getTags().stream()
            .collect(Collectors.toMap(TagDTO::getId, t -> t));
        Map<Long, TagDTO> modifiedTags = modifiedPost.getTags().stream()
            .collect(Collectors.toMap(TagDTO::getId, t -> t));

        for (Long id : originalTags.keySet()) {
            if (!modifiedTags.containsKey(id)) {
                postDAO.unlinkTag(originalPost.getId(), id);
            }
        }

        for (Map.Entry<Long, TagDTO> entry : modifiedTags.entrySet()) {
            if (!originalTags.containsKey(entry.getKey())) {
                TagDTO upserted = tagDAO.upsert(entry.getValue());
                postDAO.linkTag(originalPost.getId(), upserted.getId());
            }
        }
    }
}

