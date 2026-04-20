package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = "spring")
public interface PostCommentMapper {

    @Mapping(target = "author", expression = "java(comment.getAuthor().getFirstName() + \" \" + comment.getAuthor().getLastName())")
    @Mapping(target = "replies", ignore = true)
    PostCommentDTO toDTO(PostComment comment);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", ignore = true)
    PostComment toEntity(PostCommentDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", ignore = true)
    void updateFromDTO(PostCommentDTO dto, @MappingTarget PostComment entity);
}

