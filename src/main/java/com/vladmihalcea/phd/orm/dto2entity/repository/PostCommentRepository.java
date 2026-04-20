package com.vladmihalcea.phd.orm.dto2entity.repository;

import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

}
