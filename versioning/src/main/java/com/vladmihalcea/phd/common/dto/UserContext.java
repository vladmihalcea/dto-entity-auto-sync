package com.vladmihalcea.phd.common.dto;

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
