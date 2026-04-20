package com.vladmihalcea.phd.orm.dto2entity.dto.converter;

import com.vladmihalcea.phd.orm.dto2entity.domain.Post;
import com.vladmihalcea.phd.orm.dto2entity.domain.PostComment;
import com.vladmihalcea.phd.orm.dto2entity.domain.Tag;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostCommentDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.TagDTO;
import com.vladmihalcea.phd.orm.dto2entity.dto.UserContext;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Vlad Mihalcea
 */
public class PostDTOConverter {

    public static PostDTO of(Post post) {
        return new PostDTO()
            .setId(post.getId())
            .setTitle(post.getTitle())
            .setAuthor(
                String.join(" ",
                    post.getAuthor().getFirstName(),
                    post.getAuthor().getLastName()
                )
            )
            .setVotes(post.getVotes())
            .setReplies(toHierarchicalPostCommentDTO(post.getComments()))
            .setTags(
                post.getTags().stream().map(
                    t -> new TagDTO()
                        .setId(t.getId())
                        .setName(t.getName())
                ).collect(Collectors.toCollection((HashSet::new)))
            );
    }

    public static Post of(PostDTO postDTO) {
        Post post = new Post()
            .setId(postDTO.getId())
            .setTitle(postDTO.getTitle())
            .setContent(postDTO.getContent())
            .setVotes(postDTO.getVotes());
        if(postDTO.getId() == null){
            post.setAuthor(UserDTOConverter.of(UserContext.getCurrentUser()));
        }

        postDTO.getReplies().forEach(commentDTO -> {
            PostComment postComment = new PostComment()
                .setId(commentDTO.getId())
                .setContent(commentDTO.getContent())
                .setVotes(postDTO.getVotes());
            if(commentDTO.getId() == null){
                postComment.setAuthor(UserDTOConverter.of(UserContext.getCurrentUser()));
            }
            post.addComment(postComment);
        });

        postDTO.getTags().forEach(tagDTO -> post.addTag(
            new Tag()
                .setId(tagDTO.getId())
                .setName(tagDTO.getName())
        ));

        return post;
    }

    private static List<PostCommentDTO> toHierarchicalPostCommentDTO(List<PostComment> postComments) {
        Map<Long, PostCommentDTO> postCommentDTOMap = new LinkedHashMap<>();

        for(PostComment pc : postComments) {
            PostCommentDTO postCommentDTO = new PostCommentDTO()
                .setId(pc.getId())
                .setContent(pc.getContent())
                .setAuthor(String.join(" ",
                    pc.getAuthor().getFirstName(),
                    pc.getAuthor().getLastName()
                ));
            if(pc.getParent() != null) {
                postCommentDTOMap.get(pc.getParent().getId()).addReply(postCommentDTO);
            } else {
                postCommentDTOMap.put(pc.getId(), postCommentDTO);
            }
        }

        return new ArrayList<>(postCommentDTOMap.values());
    }
}
