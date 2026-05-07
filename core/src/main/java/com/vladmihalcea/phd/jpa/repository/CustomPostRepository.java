package com.vladmihalcea.phd.jpa.repository;

import com.vladmihalcea.phd.jpa.view.PostDto;

/**
 * @author Vlad Mihalcea
 */
public interface CustomPostRepository {
    PostDto loadPostDTOUsingMultiset(Long id);
}
