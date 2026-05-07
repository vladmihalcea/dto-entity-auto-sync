package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostDto;

/**
 * @author Vlad Mihalcea
 */
public interface PostDao {
    PostDto findById(Long id);
}
