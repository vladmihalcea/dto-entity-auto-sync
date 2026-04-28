package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.common.dto.TagDTO;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TagMapper {

    TagDTO toDTO(Tag tag);

    Tag toEntity(TagDTO dto);
}
