package com.vladmihalcea.phd.common.service;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.TagDao;
import com.vladmihalcea.phd.common.dto.DtoMerger;
import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional
public class ForumService {

    private final PostDao postDAO;
    private final PostCommentDao postCommentDAO;
    private final TagDao tagDAO;
    private final DtoMerger dtoMerger;

    public ForumService(PostDao postDAO, PostCommentDao postCommentDAO, TagDao tagDAO) {
        this.postDAO = postDAO;
        this.postCommentDAO = postCommentDAO;
        this.tagDAO = tagDAO;
        this.dtoMerger = new DtoMerger(postDAO, postCommentDAO, tagDAO);
    }

    @Transactional(readOnly = true)
    public PostDto loadPostDTO(Long postId) {
        PostDto post = postDAO.findById(postId);
        List<PostCommentDto> replies = postCommentDAO.findByPostId(postId);
        post.setReplies(replies);
        post.setTags(tagDAO.findByPostId(postId));
        return post;
    }

    @Transactional(readOnly = true)
    public PostDto loadPostDTOUsingMultiset(Long postId) {
        return postDAO.findByIdUsingMultiset(postId);
    }

    @Transactional(readOnly = true)
    public JsonNode loadPostJsonNodeUsingMultiset(Long postId) {
        return postDAO.findByIdAsJsonNodeUsingMultiset(postId);
    }

    @Transactional(readOnly = true)
    public String loadPostJsonStringUsingMultiset(Long postId) {
        return postDAO.findByIdAsJsonStringUsingMultiset(postId);
    }

    /*
     * Each write takes the post version the client has read and returns the new one.
     * The root version is incremented before the child row is written,
     * so a stale version is rejected before any change is made.
     */

    public short updatePost(PostDto post) {
        return postDAO.update(post);
    }

    public short createPostComment(Long postId, short version, PostCommentDto comment) {
        short nextVersion = postDAO.incrementVersion(postId, version);
        postCommentDAO.insert(postId, comment);
        return nextVersion;
    }

    public short updatePostComment(Long postId, short version, PostCommentDto comment) {
        short nextVersion = postDAO.incrementVersion(postId, version);
        postCommentDAO.update(postId, comment);
        return nextVersion;
    }

    public short removePostComment(Long postId, short version, Long commentId) {
        short nextVersion = postDAO.incrementVersion(postId, version);
        postCommentDAO.deleteChildrenOf(postId, commentId);
        postCommentDAO.delete(postId, commentId);
        return nextVersion;
    }

    public short createTag(Long postId, short version, TagDto tag) {
        short nextVersion = postDAO.incrementVersion(postId, version);
        tagDAO.upsert(tag);
        postDAO.linkTag(postId, tag.getId());
        return nextVersion;
    }

    public short removeTag(Long postId, short version, TagDto tag) {
        short nextVersion = postDAO.incrementVersion(postId, version);
        postDAO.unlinkTag(postId, tag.getId());
        return nextVersion;
    }

    @Transactional
    public short diffMerge(PostDto originalPost, PostDto modifiedPost) {
        return dtoMerger.diffMergePostDTO(originalPost, modifiedPost);
    }
}

