package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.AbstractPostDAO;
import com.vladmihalcea.phd.common.dto.PostDTO;
import com.vladmihalcea.phd.common.dto.UserDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostDAO implements AbstractPostDAO {

    private static final String FIND_BY_ID =
        "SELECT p.id, p.title, p.content, p.votes, " +
        "u.id AS author_id, u.email, u.first_name, u.last_name " +
        "FROM posts p JOIN users u ON u.id = p.author_id WHERE p.id = ?";

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

    public PostDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostDTO findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, new PostRowMapper(), id);
    }

    @Override
    public void insert(PostDTO post) {
        jdbcTemplate.update(INSERT,
            post.getId(), post.getTitle(), post.getContent(),
            post.getVotes(), post.getAuthor().getId());
    }

    @Override
    public void update(PostDTO post) {
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

    public static class PostRowMapper implements RowMapper<PostDTO> {
        @Override
        public PostDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDTO author = new UserDTO()
                .setId(rs.getLong("author_id"))
                .setEmail(rs.getString("email"))
                .setFirstName(rs.getString("first_name"))
                .setLastName(rs.getString("last_name"));
            return new PostDTO()
                .setId(rs.getLong("id"))
                .setTitle(rs.getString("title"))
                .setContent(rs.getString("content"))
                .setVotes(rs.getInt("votes"))
                .setAuthor(author);
        }
    }
}



