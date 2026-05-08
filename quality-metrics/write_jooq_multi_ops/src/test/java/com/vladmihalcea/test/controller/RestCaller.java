package com.vladmihalcea.test.controller;

import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.TagDto;
import org.springframework.web.client.RestTemplate;

/**
 * @author Vlad Mihalcea
 */
public class RestCaller {

    private final int port;
    private final RestTemplate restTemplate;

    public RestCaller(int port, RestTemplate restTemplate) {
        this.port = port;
        this.restTemplate = restTemplate;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    public PostDto loadPostDTO(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/post?postId={postId}",
            PostDto.class,
            postId
        );
    }

    public void updatePost(PostDto postDTO) {
        restTemplate.put(
            baseUrl() + "/post",
            postDTO,
            Void.class
        );
    }

    public void createTag(Long postId, TagDto tagDTO) {
        restTemplate.postForObject(
            baseUrl() + "/post/{postId}/tag",
            tagDTO,
            TagDto.class,
            postId
        );
    }

    public void removeTag(Long postId, TagDto tag) {
        restTemplate.delete(
            baseUrl() + "/post/{postId}/tag/{tagId}",
            postId,
            tag.getId()
        );
    }

    public void createPostComment(Long postId, PostCommentDto newReply) {
        restTemplate.postForObject(
            baseUrl() + "/post/{postId}/comment",
            newReply,
            PostCommentDto.class,
            postId
        );
    }

    public void removePostComment(Long id) {
        restTemplate.delete(
            baseUrl() + "/post/comment/{commentId}",
            id
        );
    }

    public void updatePostComment(PostCommentDto reply) {
        restTemplate.put(
            baseUrl() + "/post/comment",
            reply,
            Void.class
        );
    }
}
