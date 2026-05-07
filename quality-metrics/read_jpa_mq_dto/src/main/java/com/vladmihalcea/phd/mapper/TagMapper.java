package com.vladmihalcea.phd.mapper;

import com.vladmihalcea.phd.domain.Tag;
import com.vladmihalcea.phd.dto.TagDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TagMapper {

    TagDto toDTO(Tag tag);
}
