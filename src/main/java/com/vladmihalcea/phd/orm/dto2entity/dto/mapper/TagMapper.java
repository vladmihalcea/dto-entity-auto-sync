package com.vladmihalcea.phd.orm.dto2entity.dto.mapper;

import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import org.mapstruct.Mapper;

/**
 * @author Vlad Mihalcea
 */
@Mapper(componentModel = "spring")
public interface TagMapper {

    TagDTO toDTO(Tag tag);

    Tag toEntity(TagDTO dto);
}

