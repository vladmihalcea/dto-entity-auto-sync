package com.vladmihalcea.phd.mapper;

import com.vladmihalcea.phd.domain.PostComment;
import com.vladmihalcea.phd.dto.PostCommentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PostCommentMapper {

    @Mapping(target = "parentId", source = "parent.id")
    public abstract PostCommentDto toDTO(PostComment comment);
}
