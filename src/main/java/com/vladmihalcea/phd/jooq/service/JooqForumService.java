package com.vladmihalcea.phd.jooq.service;

import com.vladmihalcea.phd.common.dao.PostCommentDao;
import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dao.TagDao;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.service.ForumService;
import com.vladmihalcea.phd.jooq.dao.PostDaoImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Vlad Mihalcea
 */
@Service
@Primary
public class JooqForumService extends ForumService {

    private final PostDaoImpl postDAO;

    public JooqForumService(PostDao postDAO, PostCommentDao postCommentDAO,
                            TagDao tagDAO) {
        super(postDAO, postCommentDAO, tagDAO);
        this.postDAO = (PostDaoImpl) postDAO;
    }

    @Transactional(readOnly = true)
    public PostDto loadPostDTOUsingMultiset(Long postId) {
        return postDAO.findByIdUsingMultiset(postId);
    }
}

