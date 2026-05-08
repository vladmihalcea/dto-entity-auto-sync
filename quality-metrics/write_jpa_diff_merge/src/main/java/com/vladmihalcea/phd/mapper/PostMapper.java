package com.vladmihalcea.phd.mapper;

import com.vladmihalcea.phd.domain.Post;
import com.vladmihalcea.phd.domain.PostComment;
import com.vladmihalcea.phd.domain.Tag;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.service.EntityMerger;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vlad Mihalcea
 */
@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    uses = {TagMapper.class, PostCommentMapper.class, UserMapper.class}
)
public abstract class PostMapper {

    @Autowired
    private EntityMerger entityMerger;

    @Mapping(target = "replies", source = "comments")
    public abstract PostDto toDTO(Post post);

    @Mapping(target = "comments", source = "replies")
    public abstract Post toEntity(PostDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "tags", ignore = true)
    public abstract void updateFromDTO(PostDto dto, @MappingTarget Post entity);

    public void merge(PostDto postDTO, Post postFromDatabase) {
        Post postFromDTO = toEntity(postDTO);

        updateFromDTO(postDTO, postFromDatabase);

        entityMerger.mergeCollection(
            postFromDatabase.getComments(),
            postFromDTO.getComments(),
            PostComment::getId,
            c -> postFromDatabase.addComment(c),
            c -> postFromDatabase.removeComment(c),
            (source, target) -> entityMerger.mergeBasicAttributes(
                source, target)
        );

        entityMerger.mergeCollection(
            postFromDatabase.getTags(),
            postFromDTO.getTags(),
            Tag::getId,
            t -> postFromDatabase.addTag(t),
            t -> postFromDatabase.removeTag(t),
            (source, target) -> entityMerger.mergeBasicAttributes(
                source, target)
        );
    }
}
