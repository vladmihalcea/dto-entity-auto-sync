package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.vladmihalcea.phd.jpa.domain.PostComment;

/**
 * @author Vlad Mihalcea
 */
@EntityView(PostComment.class)
public interface PostCommentView {

    @IdMapping
    Long getId();

    String getContent();

    int getVotes();

    UserView getAuthor();

    @Mapping("parent.id")
    Long getParentId();
}

