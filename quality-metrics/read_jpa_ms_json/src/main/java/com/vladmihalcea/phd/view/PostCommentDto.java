package com.vladmihalcea.phd.view;

import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.vladmihalcea.phd.domain.PostComment;

/**
 * @author Vlad Mihalcea
 */
@EntityView(PostComment.class)
public interface PostCommentDto {

    @IdMapping
    Long getId();

    String getContent();

    int getVotes();

    UserDto getAuthor();

    @Mapping("parent.id")
    Long getParentId();

    class PostCommentDtoComparator implements java.util.Comparator<PostCommentDto> {
        @Override
        public int compare(PostCommentDto o1, PostCommentDto o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }
}

