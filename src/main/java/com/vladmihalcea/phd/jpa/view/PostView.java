package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.vladmihalcea.phd.jpa.domain.Post;

/**
 * @author Vlad Mihalcea
 */
@EntityView(Post.class)
public interface PostView {

    @IdMapping
    Long getId();

    String getTitle();

    String getContent();

    int getVotes();

    UserView getAuthor();
}

