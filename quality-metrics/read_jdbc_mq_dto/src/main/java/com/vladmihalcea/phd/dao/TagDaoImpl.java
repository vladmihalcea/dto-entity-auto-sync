package com.vladmihalcea.phd.dao;

import com.vladmihalcea.phd.dto.TagDto;
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

    private static final String FIND_BY_POST_ID =
        "SELECT t.id, t.name, t.description FROM tags t " +
        "JOIN post_tags pt ON pt.tag_id = t.id WHERE pt.post_id = ? ORDER BY t.id";

    private final JdbcTemplate jdbcTemplate;

    public TagDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<TagDto> findByPostId(Long postId) {
        return jdbcTemplate.query(FIND_BY_POST_ID, new TagRowMapper(), postId);
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



