package com.vladmihalcea.phd.jdbc.dao;

import com.vladmihalcea.phd.common.dao.UserDao;
import com.vladmihalcea.phd.common.dto.UserDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * @author Vlad Mihalcea
 */
@Repository
public class UserDaoImpl implements UserDao {

    private static final String INSERT =
        "INSERT INTO users (id, email, first_name, last_name) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public UserDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(UserDto user) {
        jdbcTemplate.update(INSERT, user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}


