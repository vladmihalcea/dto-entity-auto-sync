package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.UserDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class PostCommentDAOImpl implements PostCommentDao {

    private static final String FIND_BY_POST_ID =
        "SELECT pc.id, pc.content, pc.votes, pc.parent_id, " +
        "u.id AS author_id, u.email, u.first_name, u.last_name " +
        "FROM post_comments pc JOIN users u ON u.id = pc.author_id " +
        "WHERE pc.post_id = ? ORDER BY pc.id";

    private final JdbcTemplate jdbcTemplate;

    public PostCommentDAOImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PostCommentDto> findByPostId(Long postId) {
        return jdbcTemplate.query(FIND_BY_POST_ID, new PostCommentRowMapper(), postId);
    }

    public static class PostCommentRowMapper implements RowMapper<PostCommentDto> {
        @Override
        public PostCommentDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDto author = new UserDto()
                .setId(rs.getLong("author_id"))
                .setEmail(rs.getString("email"))
                .setFirstName(rs.getString("first_name"))
                .setLastName(rs.getString("last_name"));
            long parentIdRaw = rs.getLong("parent_id");
            Long parentId = rs.wasNull() ? null : parentIdRaw;
            return new PostCommentDto()
                .setId(rs.getLong("id"))
                .setContent(rs.getString("content"))
                .setVotes(rs.getInt("votes"))
                .setAuthor(author)
                .setParentId(parentId);
        }
    }
}



