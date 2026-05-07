package com.vladmihalcea.phd.mapper;

import com.vladmihalcea.phd.domain.User;
import com.vladmihalcea.phd.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserDto toDTO(User user);
}

