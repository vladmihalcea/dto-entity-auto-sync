package com.vladmihalcea.phd.jdbc.singleopsync.dao;

import com.vladmihalcea.phd.common.util.dto.UserDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class UserDAO {

    private static final String FIND_BY_ID =
        "SELECT id, email, first_name, last_name FROM users WHERE id = ?";

    private static final String FIND_BY_EMAIL =
        "SELECT id, email, first_name, last_name FROM users WHERE email = ?";

    private static final String INSERT =
        "INSERT INTO users (id, email, first_name, last_name) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UserDTO findById(Long id) {
        return jdbcTemplate.queryForObject(FIND_BY_ID, new UserRowMapper(), id);
    }

    public UserDTO findByEmail(String email) {
        return jdbcTemplate.queryForObject(FIND_BY_EMAIL, new UserRowMapper(), email);
    }

    public void insert(UserDTO user) {
        jdbcTemplate.update(INSERT, user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }

    public static class UserRowMapper implements RowMapper<UserDTO> {
        @Override
        public UserDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new UserDTO()
                .setId(rs.getLong("id"))
                .setEmail(rs.getString("email"))
                .setFirstName(rs.getString("first_name"))
                .setLastName(rs.getString("last_name"));
        }
    }
}

