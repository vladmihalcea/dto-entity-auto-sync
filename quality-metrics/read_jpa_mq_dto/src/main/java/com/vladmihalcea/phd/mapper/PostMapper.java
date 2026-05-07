package com.vladmihalcea.phd.mapper;

import com.vladmihalcea.phd.domain.Post;
import com.vladmihalcea.phd.dto.PostDto;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    uses = {TagMapper.class, PostCommentMapper.class, UserMapper.class}
)
public abstract class PostMapper {

    @Mapping(target = "replies", source = "comments")
    public abstract PostDto toDTO(Post post);
}
