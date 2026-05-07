package com.vladmihalcea.phd.dao;

/**
 * @author Vlad Mihalcea
 */
public interface PostDao {
    String findByIdAsJsonStringUsingMultiset(Long postId);
}
