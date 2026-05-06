package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.TagDao;
import com.vladmihalcea.phd.common.dto.TagDto;
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
public class TagDaoImpl implements TagDao {

    private static final String FIND_BY_NAME =
        "SELECT id, name, description FROM tags WHERE name = ?";

    private static final String FIND_BY_POST_ID =
        "SELECT t.id, t.name, t.description FROM tags t " +
        "JOIN post_tags pt ON pt.tag_id = t.id WHERE pt.post_id = ? ORDER BY t.id";

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

    public TagDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TagDto findByName(String name) {
        try {
            return jdbcTemplate.queryForObject(FIND_BY_NAME, new TagRowMapper(), name);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<TagDto> findByPostId(Long postId) {
        return jdbcTemplate.query(FIND_BY_POST_ID, new TagRowMapper(), postId);
    }

    @Override
    public void insert(TagDto tag) {
        jdbcTemplate.update(INSERT, tag.getId(), tag.getName(), null);
    }

    @Override
    public void delete(Long id) {
        jdbcTemplate.update(DELETE, id);
    }

    @Override
    public void upsert(TagDto tag) {
        jdbcTemplate.update(UPSERT, tag.getId(), tag.getName(), null, tag.getName(), null);
    }

    public static class TagRowMapper implements RowMapper<TagDto> {
        @Override
        public TagDto mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TagDto()
                .setId(rs.getLong("id"))
                .setName(rs.getString("name"));
        }
    }
}



