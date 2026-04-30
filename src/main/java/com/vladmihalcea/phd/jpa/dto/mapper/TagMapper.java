package com.vladmihalcea.phd.jpa.dto.mapper;

import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.jpa.domain.Tag;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TagMapper {

    TagDto toDTO(Tag tag);

    Tag toEntity(TagDto dto);
}
