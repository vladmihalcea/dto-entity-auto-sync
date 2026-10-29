package com.vladmihalcea.phd.common.dto;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.TagDao;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
public class DtoMerger {

    private final PostDao postDAO;
    private final PostCommentDao postCommentDAO;
    private final TagDao tagDAO;

    public DtoMerger(
            PostDao postDAO,
            PostCommentDao postCommentDAO,
            TagDao tagDAO) {
        this.postDAO = postDAO;
        this.postCommentDAO = postCommentDAO;
        this.tagDAO = tagDAO;
    }

    public void diffMergePostDTO(PostDto originalPost, PostDto modifiedPost) {
        if (!Objects.equals(originalPost.getTitle(), modifiedPost.getTitle()) ||
            !Objects.equals(originalPost.getContent(), modifiedPost.getContent()) ||
            originalPost.getVotes() != modifiedPost.getVotes()) {
            postDAO.update(modifiedPost);
        }

        Map<Long, PostCommentDto> originalComments = originalPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDto::getId, c -> c));
        Map<Long, PostCommentDto> modifiedComments = modifiedPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDto::getId, c -> c));

        for (Map.Entry<Long, PostCommentDto> entry : originalComments.entrySet()) {
            if (!modifiedComments.containsKey(entry.getKey())) {
                postCommentDAO.deleteChildrenOf(originalPost.getId(), entry.getKey());
                postCommentDAO.delete(originalPost.getId(), entry.getKey());
            }
        }

        for (Map.Entry<Long, PostCommentDto> entry : modifiedComments.entrySet()) {
            Long id = entry.getKey();
            PostCommentDto original = originalComments.get(id);
            PostCommentDto modified = entry.getValue();
            if (original == null) {
                postCommentDAO.insert(originalPost.getId(), modified);
            } else if (!Objects.equals(original.getContent(), modified.getContent()) ||
                       original.getVotes() != modified.getVotes() ||
                       !Objects.equals(original.getParentId(), modified.getParentId())) {
                postCommentDAO.update(originalPost.getId(), modified);
            }
        }

        Map<Long, TagDto> originalTags = originalPost.getTags().stream()
            .collect(Collectors.toMap(TagDto::getId, t -> t));
        Map<Long, TagDto> modifiedTags = modifiedPost.getTags().stream()
            .collect(Collectors.toMap(TagDto::getId, t -> t));

        for (Long id : originalTags.keySet()) {
            if (!modifiedTags.containsKey(id)) {
                postDAO.unlinkTag(originalPost.getId(), id);
            }
        }

        for (Map.Entry<Long, TagDto> entry : modifiedTags.entrySet()) {
            if (!originalTags.containsKey(entry.getKey())) {
                TagDto tag = entry.getValue();
                tagDAO.upsert(tag);
                postDAO.linkTag(originalPost.getId(), tag.getId());
            }
        }
    }
}
