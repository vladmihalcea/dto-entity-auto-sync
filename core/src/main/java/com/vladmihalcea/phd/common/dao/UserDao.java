package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.UserDto;

/**
 * @author Vlad Mihalcea
 */
public interface UserDao {
    void insert(UserDto user);
}
