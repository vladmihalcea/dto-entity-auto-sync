package com.vladmihalcea.phd.util;

import com.vladmihalcea.phd.dto.UserDto;

/**
 *
 * @author Vlad Mihalcea
 */
public class UserContext {

    private static final ThreadLocal<UserDto> userRequestHolder = new ThreadLocal<>();

    public static UserDto getCurrentUser() {
        return userRequestHolder.get();
    }

    public static void setCurrentUser(UserDto userDTO) {
        userRequestHolder.set(userDTO);
    }

    public static void reset() {
        userRequestHolder.remove();
    }
}
