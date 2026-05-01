package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.PostDao;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import com.vladmihalcea.phd.common.util.JsonUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostDaoImpl implements PostDao {

    private static final String FIND_BY_ID =
        "SELECT p.id, p.title, p.content, p.votes, " +
        "u.id AS author_id, u.email, u.first_name, u.last_name " +
        "FROM posts p JOIN users u ON u.id = p.author_id WHERE p.id = ?";

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
        "        join \"post_tag\" on \"post_tag\".\"post_id\" = \"tags\".\"id\" " +
        "        where \"post_tag\".\"tag_id\" = \"posts\".\"id\" " +
        " ) " +
        ") " +
        "from \"posts\" " +
        "join \"users\" on \"users\".\"id\" = \"posts\".\"author_id\" " +
        "where \"posts\".\"id\" = ?";

    private static final String INSERT =
        "INSERT INTO posts (id, title, content, votes, author_id) VALUES (?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE posts SET title = ?, content = ?, votes = ? WHERE id = ?";

    private static final String DELETE =
        "DELETE FROM posts WHERE id = ?";

    private static final String LINK_TAG =
        "INSERT INTO post_tag (post_id, tag_id) VALUES (?, ?)";

    private static final String UNLINK_TAG =
        "DELETE FROM post_tag WHERE post_id = ? AND tag_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostDto findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, new PostRowMapper(), id);
    }

    @Override
    public PostDto findByIdUsingMultiset(Long postId) {
        return JsonUtil.JSON_MAPPER.readValue(
            findByIdAsJsonStringUsingMultiset(postId),
            PostDto.class
        );
    }

    @Override
    public JsonNode findByIdAsJsonNodeUsingMultiset(Long postId) {
        return JsonUtil.JSON_MAPPER.readTree(
            findByIdAsJsonStringUsingMultiset(postId)
        );
    }

    @Override
    public String findByIdAsJsonStringUsingMultiset(Long postId) {
        return jdbcTemplate.queryForObject(FIND_BY_ID_AS_JSON, String.class, postId);
    }

    @Override
    public void insert(PostDto post) {
        jdbcTemplate.update(INSERT,
            post.getId(), post.getTitle(), post.getContent(),
            post.getVotes(), post.getAuthor().getId());
    }

    @Override
    public void update(PostDto post) {
        jdbcTemplate.update(UPDATE, post.getTitle(), post.getContent(), post.getVotes(), post.getId());
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update(DELETE, id);
    }

    @Override
    public void linkTag(Long postId, Long tagId) {
        jdbcTemplate.update(LINK_TAG, tagId, postId);
    }

    @Override
    public void unlinkTag(Long postId, Long tagId) {
        jdbcTemplate.update(UNLINK_TAG, tagId, postId);
    }

    public static class PostRowMapper implements RowMapper<PostDto> {
        @Override
        public PostDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDto author = new UserDto()
                .setId(rs.getLong("author_id"))
                .setEmail(rs.getString("email"))
                .setFirstName(rs.getString("first_name"))
                .setLastName(rs.getString("last_name"));
            return new PostDto()
                .setId(rs.getLong("id"))
                .setTitle(rs.getString("title"))
                .setContent(rs.getString("content"))
                .setVotes(rs.getInt("votes"))
                .setAuthor(author);
        }
    }
}



