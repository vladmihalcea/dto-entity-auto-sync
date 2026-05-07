package com.vladmihalcea.phd.service;

import com.vladmihalcea.phd.dao.PostDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional
public class ForumService {

    private final PostDao postDAO;

    public ForumService(PostDao postDAO) {
        this.postDAO = postDAO;
    }

    @Transactional(readOnly = true)
    public String loadPostJsonStringUsingMultiset(Long postId) {
        return postDAO.findByIdAsJsonStringUsingMultiset(postId);
    }
}

