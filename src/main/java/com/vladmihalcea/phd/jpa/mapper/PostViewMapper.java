package com.vladmihalcea.phd.jpa.mapper;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.dto.UserDto;
import com.vladmihalcea.phd.jpa.view.PostCommentView;
import com.vladmihalcea.phd.jpa.view.PostWithCommentsAndTagsView;
import com.vladmihalcea.phd.jpa.view.TagView;
import com.vladmihalcea.phd.jpa.view.UserView;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PostViewMapper {

    @Mapping(target = "replies", source = "comments")
    PostDto toDTO(PostWithCommentsAndTagsView view);

    PostCommentDto toDTO(PostCommentView view);

    TagDto toDTO(TagView view);

    UserDto toDTO(UserView view);
}

