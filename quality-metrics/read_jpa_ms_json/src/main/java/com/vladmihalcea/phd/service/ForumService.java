package com.vladmihalcea.phd.service;

import com.vladmihalcea.phd.dao.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional
public class ForumService {

    private final PostRepository postRepository;

    public ForumService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public com.vladmihalcea.phd.view.PostDto loadPostDTOUsingMultiset(Long id) {
        return postRepository.loadPostDTOUsingMultiset(id);
    }
}

