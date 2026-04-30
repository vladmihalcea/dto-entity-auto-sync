package com.vladmihalcea.phd.jpa.dto.mapper;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.jpa.domain.PostComment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PostCommentMapper {

    @PersistenceContext
    private EntityManager entityManager;

    @Mapping(target = "parentId", source = "parent.id")
    public abstract PostCommentDto toDTO(PostComment comment);

    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "parentReference")
    public abstract PostComment toEntity(PostCommentDto dto);

    @Named("parentReference")
    protected PostComment parentReference(Long parentId) {
        return parentId != null ?
            entityManager.getReference(PostComment.class, parentId) :
            null;
    }
}
