package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.UserDto;

/**
 * The {@link UserDao}
 *
 * @author Vlad Mihalcea
 * @since 1.x.y
 */
public interface UserDao {
    void insert(UserDto user);
}
