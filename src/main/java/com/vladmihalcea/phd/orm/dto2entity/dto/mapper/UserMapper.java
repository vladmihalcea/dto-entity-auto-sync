package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.User;
import com.vladmihalcea.phd.orm.dto2entity.dto.UserDTO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserDTO toDTO(User user);

    User toEntity(UserDTO dto);
}

