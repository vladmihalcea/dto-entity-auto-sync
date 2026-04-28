package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dto.PostCommentDTO;
import com.vladmihalcea.phd.common.dto.UserDTO;
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
public class PostCommentDAO {

    private static final String FIND_BY_POST_ID =
        "SELECT pc.id, pc.content, pc.votes, pc.parent_id, " +
        "u.id AS author_id, u.email, u.first_name, u.last_name " +
        "FROM post_comments pc JOIN users u ON u.id = pc.author_id " +
        "WHERE pc.post_id = ? ORDER BY pc.id";

    private static final String INSERT =
        "INSERT INTO post_comments (id, content, votes, post_id, author_id, parent_id) " +
        "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE =
        "UPDATE post_comments SET content = ?, votes = ?, parent_id = ? WHERE id = ?";

    private static final String DELETE =
        "DELETE FROM post_comments WHERE id = ?";

    private static final String DELETE_CHILDREN_RECURSIVE =
        "WITH RECURSIVE descendants AS (" +
        "  SELECT id FROM post_comments WHERE parent_id = ? " +
        "  UNION ALL " +
        "  SELECT c.id FROM post_comments c JOIN descendants d ON c.parent_id = d.id" +
        ") DELETE FROM post_comments WHERE id IN (SELECT id FROM descendants)";

    private final JdbcTemplate jdbcTemplate;

    public PostCommentDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PostCommentDTO> findByPostId(Long postId) {
        return jdbcTemplate.query(FIND_BY_POST_ID, new PostCommentRowMapper(), postId);
    }

    public void insert(Long postId, PostCommentDTO comment) {
        jdbcTemplate.update(INSERT,
            comment.getId(), comment.getContent(), comment.getVotes(),
            postId, comment.getAuthor().getId(), comment.getParentId());
    }

    public void update(PostCommentDTO comment) {
        jdbcTemplate.update(UPDATE,
            comment.getContent(), comment.getVotes(), comment.getParentId(), comment.getId());
    }

    public void delete(Long id) {
        jdbcTemplate.update(DELETE, id);
    }

    public void deleteChildrenOf(Long parentId) {
        jdbcTemplate.update(DELETE_CHILDREN_RECURSIVE, parentId);
    }

    public static class PostCommentRowMapper implements RowMapper<PostCommentDTO> {
        @Override
        public PostCommentDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserDTO author = new UserDTO()
                .setId(rs.getLong("author_id"))
                .setEmail(rs.getString("email"))
                .setFirstName(rs.getString("first_name"))
                .setLastName(rs.getString("last_name"));
            long parentIdRaw = rs.getLong("parent_id");
            Long parentId = rs.wasNull() ? null : parentIdRaw;
            return new PostCommentDTO()
                .setId(rs.getLong("id"))
                .setContent(rs.getString("content"))
                .setVotes(rs.getInt("votes"))
                .setAuthor(author)
                .setParentId(parentId);
        }
    }
}



