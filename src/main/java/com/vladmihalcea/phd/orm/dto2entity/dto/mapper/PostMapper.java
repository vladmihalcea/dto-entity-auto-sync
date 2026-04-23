package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

import static com.vladmihalcea.phd.orm.dto2entity.dto.mapper.DTOMapperUtil.mergeCollection;

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
    private PostCommentMapper postCommentMapper;

    @Autowired
    private TagMapper tagMapper;

    @Mapping(target = "replies", source = "comments")
    public abstract PostDTO toDTO(Post post);

    @Mapping(target = "comments", source = "replies")
    @Mapping(target = "tags", source = "tags")
    public abstract Post toEntity(PostDTO dto);


    /**
     * Updates all scalar properties of the target entity from the DTO.
     * Collections (comments, tags) are ignored — they require
     * diff-based merge handled by {@link #merge}.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "tags", ignore = true)
    public abstract void updateFromDTO(PostDTO dto, @MappingTarget Post entity);

    /**
     * Full merge: converts the DTO to entity, updates scalar properties on the
     * database entity, then diff-merges comment and tag collections.
     */
    public void merge(PostDTO postDTO, Post postFromDatabase) {
        Post postFromDTO = toEntity(postDTO);

        updateFromDTO(postDTO, postFromDatabase);

        mergeCollection(
            postFromDatabase.getComments(),
            postFromDTO.getComments(),
            PostComment::getId,
            c -> postFromDatabase.addComment(c),
            c -> postFromDatabase.removeComment(c),
            (source, target) -> postCommentMapper.updateFromEntity(source, target)
        );

        mergeCollection(
            postFromDatabase.getTags(),
            postFromDTO.getTags(),
            Tag::getId,
            t -> postFromDatabase.addTag(t),
            t -> postFromDatabase.removeTag(t),
            (source, target) -> tagMapper.updateFromEntity(source, target)
        );
    }

    protected Set<TagDTO> toTagDTOSet(Set<Tag> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream()
            .map(t -> new TagDTO()
                .setId(t.getId())
                .setName(t.getName())
            )
            .collect(Collectors.toSet());
    }
}
