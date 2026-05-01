package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.vladmihalcea.phd.jpa.domain.Tag;

/**
 * @author Vlad Mihalcea
 */
@EntityView(Tag.class)
public interface TagView {

    @IdMapping
    Long getId();

    String getName();
}

