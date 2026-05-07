package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.view.PostDto;

/**
 * @author Vlad Mihalcea
 */
public interface CustomPostRepository {
    PostDto loadPostDTOUsingMultiset(Long id);
}
