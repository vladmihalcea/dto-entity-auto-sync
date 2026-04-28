package com.vladmihalcea.phd.common.util.dto;

/**
 *
 * @author Vlad Mihalcea
 */
public class UserContext {

    private static final ThreadLocal<UserDTO> userRequestHolder = new ThreadLocal<>();

    public static UserDTO getCurrentUser() {
        return userRequestHolder.get();
    }

    public static void setCurrentUser(UserDTO userDTO) {
        userRequestHolder.set(userDTO);
    }

    public static void reset() {
        userRequestHolder.remove();
    }
}
