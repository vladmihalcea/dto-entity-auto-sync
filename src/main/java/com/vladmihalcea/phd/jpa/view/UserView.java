package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.vladmihalcea.phd.jpa.domain.User;

/**
 * @author Vlad Mihalcea
 */
@EntityView(User.class)
public interface UserView {

    @IdMapping
    Long getId();

    String getEmail();

    String getFirstName();

    String getLastName();
}

