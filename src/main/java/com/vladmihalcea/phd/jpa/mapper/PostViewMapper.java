package com.vladmihalcea.phd.jpa.mapper;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PostViewMapper {

    PostDto toDTO(com.vladmihalcea.phd.jpa.view.PostDto view);

    PostCommentDto toDTO(com.vladmihalcea.phd.jpa.view.PostCommentDto view);

    TagDto toDTO(com.vladmihalcea.phd.jpa.view.TagDto view);

    UserDto toDTO(com.vladmihalcea.phd.jpa.view.UserDto view);
}

