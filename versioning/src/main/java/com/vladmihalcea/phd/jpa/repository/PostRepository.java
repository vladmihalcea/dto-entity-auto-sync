package com.vladmihalcea.phd.jpa.repository;

import com.vladmihalcea.phd.jpa.domain.Post;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.AvailableHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, CustomPostRepository {

    @Query("""
        select p
        from Post p
        left join fetch p.author
        left join fetch p.comments pc
        left join fetch pc.author
        left join fetch pc.parent
        where p.id = :id
        order by pc.id
        """)
    Post findByIdWithComments(@Param("id") Long id);

    @Query("""
        select p
        from Post p
        left join fetch p.tags t
        left join fetch t.posts
        where p.id = :id
        order by t.id
        """)
    Post findByIdWithTags(@Param("id") Long id);

    @Query("""
        select p
        from Post p
        left join fetch p.author
        left join fetch p.comments pc
        left join fetch pc.author
        left join fetch pc.parent
        where p.id = :id
        order by pc.id
        """)
    @QueryHints(
        @QueryHint(name = AvailableHints.HINT_READ_ONLY, value = "true")
    )
    Post findReadOnlyByIdWithAuthorAndComments(@Param("id") Long id);

    @Query("""
        select p
        from Post p
        left join fetch p.tags t
        left join fetch t.posts
        where p.id = :id
        order by t.id
        """)
    @QueryHints(
        @QueryHint(name = AvailableHints.HINT_READ_ONLY, value = "true")
    )
    Post findReadOnlyByIdWithTags(@Param("id") Long id);
}
