package com.vladmihalcea.phd.service;

import com.vladmihalcea.phd.dao.PostCommentDao;
import com.vladmihalcea.phd.dao.PostDao;
import com.vladmihalcea.phd.dao.TagDao;
import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.PostDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public void diffMerge(PostDto originalPost, PostDto modifiedPost) {
        dtoMerger.diffMergePostDTO(originalPost, modifiedPost);
    }
}

