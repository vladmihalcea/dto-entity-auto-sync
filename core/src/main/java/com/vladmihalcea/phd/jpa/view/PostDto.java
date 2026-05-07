package com.vladmihalcea.phd.jpa.view;

import com.blazebit.persistence.view.CollectionMapping;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;
import com.blazebit.persistence.view.Mapping;
import com.vladmihalcea.phd.jpa.domain.Post;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static com.blazebit.persistence.view.FetchStrategy.MULTISET;

/**
 * @author Vlad Mihalcea
 */
@EntityView(Post.class)
public interface PostDto {

    @IdMapping
    Long getId();

    String getTitle();

    String getContent();

    UserDto getAuthor();

    int getVotes();

    @Mapping(value = "comments", fetch = MULTISET)
    @CollectionMapping(comparator = PostCommentDto.PostCommentDtoComparator.class)
    SortedSet<PostCommentDto> getReplies();

    @Mapping(fetch = MULTISET)
    @CollectionMapping(comparator = TagDto.TagDtoComparator.class)
    SortedSet<TagDto> getTags();
}

