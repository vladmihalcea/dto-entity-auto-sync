package com.vladmihalcea.phd.jpa.dto2entity.dto.mapper;

import com.vladmihalcea.phd.common.dto.UserDto;
import com.vladmihalcea.phd.jpa.dto2entity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserDto toDTO(User user);

    User toEntity(UserDto dto);
}

