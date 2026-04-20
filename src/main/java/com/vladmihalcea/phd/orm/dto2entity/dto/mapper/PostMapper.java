package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.UserContext;
import com.vladmihalcea.phd.orm.dto2entity.dto.converter.UserDTOConverter;
import org.mapstruct.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
@Mapper(
    componentModel = "spring",
    collectionMappingStrategy = CollectionMappingStrategy.ADDER_PREFERRED,
    uses = {TagMapper.class, PostCommentMapper.class, UserMapper.class}
)
public interface PostMapper {

    @Mapping(target = "author", expression = "java(post.getAuthor().getFirstName() + \" \" + post.getAuthor().getLastName())")
    @Mapping(target = "replies", source = "comments", qualifiedByName = "toHierarchicalPostCommentDTOs")
    PostDTO toDTO(Post post);

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "comments", source = "replies")
    @Mapping(target = "tags", source = "tags")
    Post toEntity(PostDTO dto);

    @AfterMapping
    default void setAuthorFromContext(PostDTO dto, @MappingTarget Post post) {
        if (dto.getId() == null) {
            post.setAuthor(UserDTOConverter.of(UserContext.getCurrentUser()));
        }
        for (PostComment comment : post.getComments()) {
            if (comment.getId() == null) {
                comment.setAuthor(UserDTOConverter.of(UserContext.getCurrentUser()));
            }
        }
    }

    /**
     * Updates all scalar properties of the target entity from the DTO.
     * Collections (comments, tags) and author are ignored — they require
     * diff-based merge handled separately by ForumService.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "tags", ignore = true)
    void updateFromDTO(PostDTO dto, @MappingTarget Post entity);

    /**
     * Converts flat PostComment list to hierarchical PostCommentDTO tree.
     * Parent comments become top-level entries; child comments are nested
     * under their parent's replies list.
     */
    @Named("toHierarchicalPostCommentDTOs")
    default List<PostCommentDTO> toHierarchicalPostCommentDTOs(List<PostComment> comments) {
        if (comments == null) {
            return null;
        }
        Map<Long, PostCommentDTO> map = new LinkedHashMap<>();

        for (PostComment pc : comments) {
            PostCommentDTO dto = new PostCommentDTO()
                .setId(pc.getId())
                .setContent(pc.getContent())
                .setAuthor(String.join(" ",
                    pc.getAuthor().getFirstName(),
                    pc.getAuthor().getLastName()
                ));
            if (pc.getParent() != null) {
                map.get(pc.getParent().getId()).addReply(dto);
            } else {
                map.put(pc.getId(), dto);
            }
        }

        return new ArrayList<>(map.values());
    }

    default Set<TagDTO> toTagDTOSet(Set<Tag> tags) {
        if (tags == null) {
            return null;
        }
        return tags.stream()
            .map(t -> new TagDTO().setId(t.getId()).setName(t.getName()))
            .collect(Collectors.toCollection(HashSet::new));
    }
}
