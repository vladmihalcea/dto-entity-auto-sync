package com.vladmihalcea.phd.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.vladmihalcea.phd.domain.User;

/**
 * @author Vlad Mihalcea
 */
@EntityView(User.class)
public interface UserDto {

    @IdMapping
    Long getId();

    String getEmail();

    String getFirstName();

    String getLastName();
}

