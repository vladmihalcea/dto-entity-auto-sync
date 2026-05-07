package com.vladmihalcea.phd.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.vladmihalcea.phd.domain.Tag;

/**
 * @author Vlad Mihalcea
 */
@EntityView(Tag.class)
public interface TagDto {

    @IdMapping
    Long getId();

    String getName();

    class TagDtoComparator implements java.util.Comparator<TagDto> {
        @Override
        public int compare(TagDto o1, TagDto o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }
}

