package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Generic diff-based collection merge.
     * <p>
     * Removes items present in {@code dbCollection} but absent from {@code dtoCollection},
     * updates matching items via {@code updateFn}, and adds new items via {@code addFn}.
     * Equality for remove/add detection relies on the entity {@code equals}/{@code hashCode}
     * contract (e.g. id for PostComment, name for Tag).
     *
     * @param dbCollection  the live managed collection from the database entity
     * @param dtoCollection the detached collection built from the DTO
     * @param keyExtractor  extracts the key used to pair existing with incoming items for update
     * @param addChildFunction         adds a new item to the parent entity (should maintain bidirectional links)
     * @param removeChildFunction      removes an item from the parent entity (should maintain bidirectional links)
     * @param updateChildFunction      copies mutable properties from the incoming item to the existing managed item
     */
    protected <E, K> void mergeCollection(
        Collection<E> dbCollection,
        Collection<E> dtoCollection,
        Function<E, K> keyExtractor,
        Consumer<E> addChildFunction,
        Consumer<E> removeChildFunction,
        BiConsumer<E, E> updateChildFunction
    ) {
        // Identify new items and items to update
        List<E> toAdd = new ArrayList<>(dtoCollection);
        toAdd.removeAll(dbCollection);

        // Remove items no longer present in the DTO
        List<E> toRemove = new ArrayList<>(dbCollection);
        toRemove.removeAll(dtoCollection);
        toRemove.forEach(removeChildFunction);

        // Update existing items
        Map<K, E> dbMap = dbCollection.stream()
            .collect(Collectors.toMap(keyExtractor, Function.identity()));

        for (E incoming : dtoCollection) {
            if (!toAdd.contains(incoming)) {
                E existing = dbMap.get(keyExtractor.apply(incoming));
                if (existing != null) {
                    updateChildFunction.accept(incoming, existing);
                }
            }
        }

        // Add new items
        toAdd.forEach(addChildFunction);
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
