package com.vladmihalcea.phd.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostDaoImpl implements PostDao {

    private static final String FIND_BY_ID_AS_JSON =
        "select jsonb_build_object( " +
        " 'id', \"posts\".\"id\",  " +
        " 'title', \"posts\".\"title\",  " +
        " 'content', \"posts\".\"content\",  " +
        " 'votes', \"posts\".\"votes\",  " +
        " 'author', jsonb_build_object( " +
        "  'id', \"users\".\"id\",  " +
        "  'email', \"users\".\"email\",  " +
        "  'firstName', \"users\".\"first_name\",  " +
        "  'lastName', \"users\".\"last_name\" " +
        " ),  " +
        " 'replies', ( " +
        "  select coalesce( " +
        "   jsonb_agg( " +
        "    jsonb_build_object( " +
        "     'id', \"post_comments\".\"id\", " +
        "     'content', \"post_comments\".\"content\", " +
        "     'votes', \"post_comments\".\"votes\", " +
        "     'parentId', \"post_comments\".\"parent_id\", " +
        "     'author', jsonb_build_object( " +
        "      'id', \"cu\".\"id\", " +
        "      'email', \"cu\".\"email\", " +
        "      'firstName', \"cu\".\"first_name\", " +
        "      'lastName', \"cu\".\"last_name\" " +
        "     ) " +
        "    ) " +
        "    order by \"post_comments\".\"id\" " +
        "   ), " +
        "   '[]'::jsonb " +
        "  ) " +
        "  from \"post_comments\" " +
        "  join \"users\" as \"cu\" on \"cu\".\"id\" = \"post_comments\".\"author_id\" " +
        "  where \"post_comments\".\"post_id\" = \"posts\".\"id\" " +
        " ), " +
        " 'tags', ( " +
        "  select coalesce( " +
        "   jsonb_agg( " +
        "    jsonb_build_object( " +
        "     'id', \"tags\".\"id\",  " +
        "     'name', \"tags\".\"name\" " +
        "    ) " +
        "    order by \"tags\".\"id\" " +
        "   ),  " +
        "   '[]'::jsonb " +
        "  ) " +
        "  from \"tags\" " +
        "  join \"post_tags\" on \"post_tags\".\"tag_id\" = \"tags\".\"id\" " +
        "  where \"post_tags\".\"post_id\" = \"posts\".\"id\" " +
        " ) " +
        ") " +
        "from \"posts\" " +
        "join \"users\" on \"users\".\"id\" = \"posts\".\"author_id\" " +
        "where \"posts\".\"id\" = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String findByIdAsJsonStringUsingMultiset(Long postId) {
        return jdbcTemplate.queryForObject(FIND_BY_ID_AS_JSON, String.class, postId);
    }
}



