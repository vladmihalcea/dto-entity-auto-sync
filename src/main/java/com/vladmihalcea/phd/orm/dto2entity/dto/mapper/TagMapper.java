package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = "spring")
public interface TagMapper {

    TagDTO toDTO(Tag tag);

    Tag toEntity(TagDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "posts", ignore = true)
    void updateFromEntity(Tag source, @MappingTarget Tag target);
}
