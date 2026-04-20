package com.vladmihalcea.phd.orm.dto2entity.service;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.mapper.PostMapper;
import com.vladmihalcea.phd.orm.dto2entity.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
@Transactional(readOnly = true)
public class ForumService {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostMapper postMapper;

    public PostDTO findWithCommentsAndTagsById(Long id) {
        Post post = postRepository.findByIdWithAuthorAndComments(id);
        LOGGER.debug("Post [{}] fetched with comments", post.getId());
        post = postRepository.findByIdWithTags(id);
        return postMapper.toDTO(post);
    }

    @Transactional(readOnly = true)
    public Post convertDTOToPost(PostDTO postDTO) {
        Post postFromDatabase = postRepository.findByIdWithAuthorAndComments(postDTO.getId());
        postMapper.merge(postDTO, postFromDatabase);
        return postFromDatabase;
    }

    @Transactional
    public void updatePost(Post post) {
        postRepository.save(post);
    }
}
