package com.vladmihalcea.phd.service;

import com.vladmihalcea.phd.dao.PostRepository;
import com.vladmihalcea.phd.domain.Post;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.mapper.PostMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional
public class ForumService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;

    public ForumService(PostRepository postRepository, PostMapper postMapper) {
        this.postRepository = postRepository;
        this.postMapper = postMapper;
    }

    @Transactional(readOnly = true)
    public PostDto loadPostDTO(Long id) {
        return postMapper.toDTO(
            loadPostEntityWithAllAssociations(id)
        );
    }

    private Post loadPostEntityWithAllAssociations(Long id) {
        Post post = postRepository.findByIdWithComments(id);
        return postRepository.findByIdWithTags(post.getId());
    }
}

