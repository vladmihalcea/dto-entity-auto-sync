package com.vladmihalcea.phd.common.controller;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

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

    public JsonNode loadPostJsonNode(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postJsonNode?postId={postId}",
            JsonNode.class,
            postId
        );
    }

    public String loadPostJsonString(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postJsonString?postId={postId}",
            String.class,
            postId
        );
    }

    public String loadPostDTOUsingMultiset(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postMultiset?postId={postId}",
            String.class,
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

    public void diffMerge(PostDto originalPost, PostDto modifiedPost) {
        restTemplate.postForObject(
            baseUrl() + "/post/diff-merge",
            new PostDto[]{originalPost, modifiedPost},
            Void.class
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
