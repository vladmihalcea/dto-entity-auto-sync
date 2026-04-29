package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.AbstractUserDAO;
import com.vladmihalcea.phd.common.dto.UserDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class UserDAO implements AbstractUserDAO {

    private static final String INSERT =
        "INSERT INTO users (id, email, first_name, last_name) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public UserDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(UserDTO user) {
        jdbcTemplate.update(INSERT, user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}


