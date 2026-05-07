package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, CustomPostRepository{
}
