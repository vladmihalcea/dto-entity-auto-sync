package com.vladmihalcea.phd.common.dto;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.TagDao;

import java.util.List;
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

    /**
     * Writes the differences between the original and the modified Post aggregate.
     * <p>
     * Without a Persistence Context, there is no flush to intercept, so any change
     * to the aggregate increments the root version explicitly. The post row is
     * written first, so that a stale version is rejected before any child row is
     * written, and its row lock serializes the concurrent writers of the aggregate.
     *
     * @return the new version of the Post aggregate
     */
    public short diffMergePostDTO(PostDto originalPost, PostDto modifiedPost) {
        Long postId = originalPost.getId();

        boolean postChanged =
            !Objects.equals(originalPost.getTitle(), modifiedPost.getTitle()) ||
            !Objects.equals(originalPost.getContent(), modifiedPost.getContent()) ||
            originalPost.getVotes() != modifiedPost.getVotes();

        Map<Long, PostCommentDto> originalComments = originalPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDto::getId, c -> c));
        Map<Long, PostCommentDto> modifiedComments = modifiedPost.getReplies().stream()
            .collect(Collectors.toMap(PostCommentDto::getId, c -> c));

        List<Long> removedComments = originalComments.keySet().stream()
            .filter(id -> !modifiedComments.containsKey(id))
            .toList();
        List<PostCommentDto> addedComments = modifiedComments.values().stream()
            .filter(c -> !originalComments.containsKey(c.getId()))
            .toList();
        List<PostCommentDto> updatedComments = modifiedComments.values().stream()
            .filter(c -> originalComments.containsKey(c.getId()) &&
                         isChanged(originalComments.get(c.getId()), c))
            .toList();

        Map<Long, TagDto> originalTags = originalPost.getTags().stream()
            .collect(Collectors.toMap(TagDto::getId, t -> t));
        Map<Long, TagDto> modifiedTags = modifiedPost.getTags().stream()
            .collect(Collectors.toMap(TagDto::getId, t -> t));

        List<Long> removedTags = originalTags.keySet().stream()
            .filter(id -> !modifiedTags.containsKey(id))
            .toList();
        List<TagDto> addedTags = modifiedTags.values().stream()
            .filter(t -> !originalTags.containsKey(t.getId()))
            .toList();

        boolean aggregateChanged = postChanged ||
            !removedComments.isEmpty() || !addedComments.isEmpty() || !updatedComments.isEmpty() ||
            !removedTags.isEmpty() || !addedTags.isEmpty();
        if (!aggregateChanged) {
            return modifiedPost.getVersion();
        }

        short version = postChanged ?
            postDAO.update(modifiedPost) :
            postDAO.incrementVersion(postId, modifiedPost.getVersion());

        for (Long id : removedComments) {
            postCommentDAO.deleteChildrenOf(postId, id);
            postCommentDAO.delete(postId, id);
        }
        for (PostCommentDto comment : addedComments) {
            postCommentDAO.insert(postId, comment);
        }
        for (PostCommentDto comment : updatedComments) {
            postCommentDAO.update(postId, comment);
        }
        for (Long id : removedTags) {
            postDAO.unlinkTag(postId, id);
        }
        for (TagDto tag : addedTags) {
            tagDAO.upsert(tag);
            postDAO.linkTag(postId, tag.getId());
        }
        return version;
    }

    private boolean isChanged(PostCommentDto original, PostCommentDto modified) {
        return !Objects.equals(original.getContent(), modified.getContent()) ||
               original.getVotes() != modified.getVotes() ||
               !Objects.equals(original.getParentId(), modified.getParentId());
    }
}
