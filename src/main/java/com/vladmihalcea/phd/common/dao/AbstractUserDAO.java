package com.vladmihalcea.phd.common.dao;

import com.vladmihalcea.phd.common.dto.UserDTO;

/**
 * The {@link AbstractUserDAO}
 *
 * @author Vlad Mihalcea
 * @since 1.x.y
 */
public interface AbstractUserDAO {
    void insert(UserDTO user);
}
