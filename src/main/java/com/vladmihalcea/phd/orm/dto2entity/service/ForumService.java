package com.vladmihalcea.phd.orm.dto2entity.service;

import com.vladmihalcea.phd.common.util.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.dto.mapper.PostMapper;
import com.vladmihalcea.phd.orm.dto2entity.repository.PostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
public class ForumService {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMapper postMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public PostDTO loadPostDTO(Long id) {
        return postMapper.toDTO(
            loadPostEntityWithAllAssociations(id)
        );
    }

    private Post loadPostEntityWithAllAssociations(Long id) {
        Post post = postRepository.findByIdWithComments(id);
        return postRepository.findByIdWithTags(post.getId());
    }

    @Transactional
    public void updatePost(PostDTO postDTO) {
        postMapper.merge(
            postDTO,
            loadPostEntityWithAllAssociations(postDTO.getId())
        );
        LOGGER.info("Auto-flush the JPA entity");
    }

    @Transactional
    public void updateDetachedPost(PostDTO postDTO) {
        LOGGER.info("Convert DTO to JPA entity");
        Post postFromDatabase = loadPostEntityWithAllAssociations(postDTO.getId());
        postMapper.merge(postDTO, postFromDatabase);
        LOGGER.info("Merge updated JPA entity");
        entityManager.detach(postFromDatabase);
        postRepository.save(postFromDatabase);
    }
}
