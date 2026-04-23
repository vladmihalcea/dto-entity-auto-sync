package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.mapstruct.*;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class PostCommentMapper {

    @PersistenceContext
    private EntityManager entityManager;

    @Mapping(target = "parentId", source = "parent.id")
    public abstract PostCommentDTO toDTO(PostComment comment);

    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "parentReference")
    public abstract PostComment toEntity(PostCommentDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", ignore = true)
    public abstract void updateFromEntity(PostComment source, @MappingTarget PostComment target);

    @Named("parentReference")
    protected PostComment parentReference(Long parentId) {
        return parentId != null ? entityManager.getReference(PostComment.class, parentId) : null;
    }
}
