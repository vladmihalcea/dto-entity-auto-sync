package com.vladmihalcea.phd.orm.dto2entity.dto.converter;

import com.vladmihalcea.phd.orm.dto2entity.domain.User;
import com.vladmihalcea.phd.orm.dto2entity.dto.UserDTO;

/**
 * @author Vlad Mihalcea
 */
public class UserDTOConverter {

    public static UserDTO of(User user) {
        return new UserDTO()
            .setId(user.getId())
            .setEmail(user.getEmail())
            .setFirstName(user.getFirstName())
            .setLastName(user.getLastName());
    }

    public static User of(UserDTO userDTO) {
        return new User()
            .setId(userDTO.getId())
            .setEmail(userDTO.getEmail())
            .setFirstName(userDTO.getFirstName())
            .setLastName(userDTO.getLastName());
    }
}
