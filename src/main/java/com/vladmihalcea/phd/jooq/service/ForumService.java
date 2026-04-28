package com.vladmihalcea.phd.jooq.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.jooq.dao.PostCommentDAO;
import com.vladmihalcea.phd.jooq.dao.PostDAO;
import com.vladmihalcea.phd.jooq.dao.TagDAO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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

    public void createPost(PostDTO post) {
        postDAO.insert(post);
    }

    public void updatePost(PostDTO post) {
        postDAO.update(post);
    }

    public void deletePost(Long postId) {
        postDAO.delete(postId);
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
        TagDTO upserted = tagDAO.upsert(tag);
        postDAO.linkTag(postId, upserted.getId());
        return upserted;
    }

    public void removeTag(Long postId, TagDTO tag) {
        postDAO.unlinkTag(postId, tag.getId());
    }

    // -------------------------------------------------------------------------
    // JSON-Patch sync
    // -------------------------------------------------------------------------

    /**
     * Applies an RFC-6902 {@link JsonPatch} to {@code originalPost} to derive
     * the desired {@link PostDTO} state, then syncs all differences to the
     * database in a single transaction using jOOQ operations.
     *
     * <p>Sync logic:
     * <ol>
     *   <li>Post scalar fields ({@code title}, {@code content}, {@code votes})
     *       — one {@code UPDATE posts} if any differ.</li>
     *   <li>Comments — removed entries trigger a recursive child-delete + delete;
     *       added entries are inserted; modified entries are updated.</li>
     *   <li>Tags — removed entries are unlinked; added entries are
     *       upserted and linked.</li>
     * </ol>
     *
     * @param originalPost snapshot of the current post state (used as the
     *                     base for the patch and for diffing)
     * @param patch        RFC-6902 patch describing the desired changes
     * @return the patched {@link PostDTO} reflecting the new state
     */
    @Transactional
    public PostDTO syncPost(PostDTO originalPost, JsonPatch patch) {
        // 1. Apply the JSON Patch to derive the desired state
        PostDTO patchedPost;
        try {
            JsonNode originalNode = PATCH_MAPPER.valueToTree(originalPost);
            JsonNode patchedNode  = patch.apply(originalNode);
            patchedPost = PATCH_MAPPER.treeToValue(patchedNode, PostDTO.class);
        } catch (JsonPatchException | IOException e) {
            throw new RuntimeException("Failed to apply JSON patch to post " + originalPost.getId(), e);
        }

        // 2. Sync post-level scalar fields
        if (!Objects.equals(originalPost.getTitle(),   patchedPost.getTitle())   ||
            !Objects.equals(originalPost.getContent(), patchedPost.getContent()) ||
            originalPost.getVotes() != patchedPost.getVotes()) {
            postDAO.update(patchedPost);
        }

        // 3. Sync comments
        Map<Long, PostCommentDTO> originalComments = originalPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDTO::getId, c -> c));
        Map<Long, PostCommentDTO> patchedComments  = patchedPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDTO::getId, c -> c));

        // Deletions — in original but absent from patched
        for (Map.Entry<Long, PostCommentDTO> entry : originalComments.entrySet()) {
            if (!patchedComments.containsKey(entry.getKey())) {
                postCommentDAO.deleteChildrenOf(entry.getKey());
                postCommentDAO.delete(entry.getKey());
            }
        }

        // Insertions and updates
        for (Map.Entry<Long, PostCommentDTO> entry : patchedComments.entrySet()) {
            Long           id       = entry.getKey();
            PostCommentDTO patched  = entry.getValue();
            PostCommentDTO original = originalComments.get(id);
            if (original == null) {
                postCommentDAO.insert(originalPost.getId(), patched);
            } else if (!Objects.equals(original.getContent(), patched.getContent()) ||
                       original.getVotes() != patched.getVotes()                    ||
                       !Objects.equals(original.getParentId(), patched.getParentId())) {
                postCommentDAO.update(patched);
            }
        }

        // 4. Sync tags
        Map<Long, TagDTO> originalTags = originalPost.getTags().stream()
            .collect(Collectors.toMap(TagDTO::getId, t -> t));
        Map<Long, TagDTO> patchedTags  = patchedPost.getTags().stream()
            .collect(Collectors.toMap(TagDTO::getId, t -> t));

        // Removals — in original but absent from patched
        for (Long id : originalTags.keySet()) {
            if (!patchedTags.containsKey(id)) {
                postDAO.unlinkTag(originalPost.getId(), id);
            }
        }

        // Additions — in patched but absent from original
        for (Map.Entry<Long, TagDTO> entry : patchedTags.entrySet()) {
            if (!originalTags.containsKey(entry.getKey())) {
                TagDTO upserted = tagDAO.upsert(entry.getValue());
                postDAO.linkTag(originalPost.getId(), upserted.getId());
            }
        }

        return patchedPost;
    }
}

