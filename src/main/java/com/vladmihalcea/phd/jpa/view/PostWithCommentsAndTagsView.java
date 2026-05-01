package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.Mapping;
import com.vladmihalcea.phd.jpa.domain.Post;

import java.util.List;
import java.util.Set;

import static com.blazebit.persistence.view.FetchStrategy.MULTISET;

/**
 * @author Vlad Mihalcea
 */
@EntityView(Post.class)
public interface PostWithCommentsAndTagsView extends PostView {

    @Mapping(fetch = MULTISET)
    List<PostCommentView> getComments();

    @Mapping(fetch = MULTISET)
    Set<TagView> getTags();
}

