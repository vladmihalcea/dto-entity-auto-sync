package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import com.vladmihalcea.phd.orm.dto2entity.repository.PostCommentRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = "spring")
public abstract class PostCommentMapper {

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Mapping(target = "parentId", source = "parent.id")
    public abstract PostCommentDTO toDTO(PostComment comment);

    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "parentReference")
    public abstract PostComment toEntity(PostCommentDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", source = "parentId", qualifiedByName = "parentReference")
    public abstract void updateFromDTO(PostCommentDTO dto, @MappingTarget PostComment entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "post", ignore = true)
    @Mapping(target = "parent", ignore = true)
    public abstract void updateFromEntity(PostComment source, @MappingTarget PostComment target);

    @Named("parentReference")
    protected PostComment parentReference(Long parentId) {
        return parentId != null ? postCommentRepository.getReferenceById(parentId) : null;
    }
}
