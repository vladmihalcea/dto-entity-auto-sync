package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.UserDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

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

    private static final String UPDATE =
        "UPDATE posts SET title = ?, content = ?, votes = ? WHERE id = ?";

    private static final String LINK_TAG =
        "INSERT INTO post_tags (post_id, tag_id) VALUES (?, ?)";

    private static final String UNLINK_TAG =
        "DELETE FROM post_tags WHERE post_id = ? AND tag_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public PostDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostDto findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, new PostRowMapper(), id);
    }

    @Override
    public void update(PostDto post) {
        jdbcTemplate.update(UPDATE, post.getTitle(), post.getContent(), post.getVotes(), post.getId());
    }

    @Override
    public void linkTag(Long postId, Long tagId) {
        jdbcTemplate.update(LINK_TAG, postId, tagId);
    }

    @Override
    public void unlinkTag(Long postId, Long tagId) {
        jdbcTemplate.update(UNLINK_TAG, postId, tagId);
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



