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

    private final JdbcTemplate jdbcTemplate;

    public PostDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PostDto findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, new PostRowMapper(), id);
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



