package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.AbstractTagDAO;
import com.vladmihalcea.phd.common.dto.TagDTO;
import org.springframework.dao.EmptyResultDataAccessException;
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
public class TagDAO implements AbstractTagDAO {

    private static final String FIND_BY_NAME =
        "SELECT id, name, description FROM tags WHERE name = ?";

    private static final String FIND_BY_POST_ID =
        "SELECT t.id, t.name, t.description FROM tags t " +
        "JOIN post_tag pt ON pt.post_id = t.id WHERE pt.tag_id = ? ORDER BY t.id";

    private static final String INSERT =
        "INSERT INTO tags (id, name, description) VALUES (?, ?, ?)";

    private static final String DELETE =
        "DELETE FROM tags WHERE id = ?";

    private static final String UPSERT =
        "INSERT INTO tags (id, name, description) " +
        "VALUES (?, ?, ?) " +
        "ON CONFLICT (id) DO UPDATE " +
        "SET name = ?, description = ?";

    private final JdbcTemplate jdbcTemplate;

    public TagDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TagDTO findByName(String name) {
        try {
            return jdbcTemplate.queryForObject(FIND_BY_NAME, new TagRowMapper(), name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<TagDTO> findByPostId(Long postId) {
        return jdbcTemplate.query(FIND_BY_POST_ID, new TagRowMapper(), postId);
    }

    @Override
    public void insert(TagDTO tag) {
        jdbcTemplate.update(INSERT, tag.getId(), tag.getName(), null);
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update(DELETE, id);
    }

    @Override
    public TagDTO upsert(TagDTO tag) {
        jdbcTemplate.update(UPSERT, tag.getId(), tag.getName(), null, tag.getName(), null);
        return tag;
    }

    public static class TagRowMapper implements RowMapper<TagDTO> {
        @Override
        public TagDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TagDTO()
                .setId(rs.getLong("id"))
                .setName(rs.getString("name"));
        }
    }
}



