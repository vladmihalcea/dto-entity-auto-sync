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

import java.util.LinkedHashSet;
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
        post.setTags(new LinkedHashSet<>(tagDAO.findByPostId(postId)));
        return post;
    }

    public void updatePost(PostDto post) {
        postDAO.update(post);
    }

    public PostCommentDto createPostComment(Long postId, PostCommentDto comment) {
        postCommentDAO.insert(postId, comment);
        return comment;
    }

    public void updatePostComment(PostCommentDto comment) {
        postCommentDAO.update(comment);
    }

    public void removePostComment(Long commentId) {
        postCommentDAO.deleteChildrenOf(commentId);
        postCommentDAO.delete(commentId);
    }

    public TagDto createTag(Long postId, TagDto tag) {
        TagDto upserted = tagDAO.upsert(tag);
        postDAO.linkTag(postId, upserted.getId());
        return upserted;
    }

    public void removeTag(Long postId, TagDto tag) {
        postDAO.unlinkTag(postId, tag.getId());
    }

    @Transactional
    public void diffMerge(PostDto originalPost, PostDto modifiedPost) {
        dtoMerger.diffMergePostDTO(originalPost, modifiedPost);
    }
}

