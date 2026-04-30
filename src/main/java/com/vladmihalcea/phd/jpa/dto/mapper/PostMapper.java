package com.vladmihalcea.phd.jpa.dto.mapper;

import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import com.vladmihalcea.phd.jpa.domain.Tag;
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

    /**
     * Updates all basic properties of the target entity from the DTO.
     * Collections (comments, tags) are ignored since they require
     * a diff-based merge that is handled by the {@link #merge} method.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "tags", ignore = true)
    public abstract void updateFromDTO(PostDto dto, @MappingTarget Post entity);

    /**
     * Converts the DTO to entity and updates the basic properties on the
     * database entity, then diff-merges the comment and tag collections.
     */
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
