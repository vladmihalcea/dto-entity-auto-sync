package com.vladmihalcea.phd.jooq.singleopsync.service;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.jooq.singleopsync.dao.PostCommentDAO;
import com.vladmihalcea.phd.jooq.singleopsync.dao.PostDAO;
import com.vladmihalcea.phd.jooq.singleopsync.dao.TagDAO;
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
}

