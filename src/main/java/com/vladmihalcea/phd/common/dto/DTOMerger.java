package com.vladmihalcea.phd.common.dto;

import com.vladmihalcea.phd.common.dao.AbstractPostCommentDAO;
import com.vladmihalcea.phd.common.dao.AbstractPostDAO;
import com.vladmihalcea.phd.common.dao.AbstractTagDAO;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
public class DTOMerger {

    private final AbstractPostDAO postDAO;
    private final AbstractPostCommentDAO postCommentDAO;
    private final AbstractTagDAO tagDAO;

    public DTOMerger(
            AbstractPostDAO postDAO,
            AbstractPostCommentDAO postCommentDAO,
            AbstractTagDAO tagDAO) {
        this.postDAO = postDAO;
        this.postCommentDAO = postCommentDAO;
        this.tagDAO = tagDAO;
    }

    public void diffMergePostDTO(PostDTO originalPost, PostDTO modifiedPost) {
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
