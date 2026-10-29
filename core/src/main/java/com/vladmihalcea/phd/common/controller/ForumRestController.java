package com.vladmihalcea.phd.common.controller;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.service.ForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

/**
 *
 * @author Vlad Mihalcea
 */
@RestController
public class ForumRestController {

    @Autowired
    private ForumService forumService;

    @GetMapping("/post")
    public PostDto findById(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostDTO(postId);
    }

    @GetMapping("/postJsonNode")
    public JsonNode findJsonNodeByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostJsonNodeUsingMultiset(postId);
    }

    @GetMapping("/postJsonString")
    public String findJsonStringByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostJsonStringUsingMultiset(postId);
    }

    /**
     * Maps the MULTISET JSON into a {@code PostDto}, which Jackson serializes again, so that, compared with
     * {@code /postJsonString}, it measures the cost of returning a typed DTO instead of forwarding the database JSON.
     */
    @GetMapping("/postMultiset")
    public PostDto findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostDTOUsingMultiset(postId);
    }

    @PutMapping("/post")
    public void updatePost(@RequestBody PostDto postDTO) {
        forumService.updatePost(postDTO);
    }

    @PostMapping("/post/{postId}/tag")
    public TagDto createTag(@PathVariable Long postId, @RequestBody TagDto tagDTO) {
        return forumService.createTag(postId, tagDTO);
    }

    @DeleteMapping("/post/{postId}/tag/{tagId}")
    public void removeTag(@PathVariable Long postId, @PathVariable Long tagId) {
        forumService.removeTag(postId, new TagDto().setId(tagId));
    }

    @PostMapping("/post/{postId}/comment")
    public PostCommentDto createPostComment(@PathVariable Long postId, @RequestBody PostCommentDto commentDTO) {
        return forumService.createPostComment(postId, commentDTO);
    }

    @DeleteMapping("/post/{postId}/comment/{commentId}")
    public void removePostComment(@PathVariable Long postId, @PathVariable Long commentId) {
        forumService.removePostComment(postId, commentId);
    }

    @PutMapping("/post/{postId}/comment")
    public void updatePostComment(@PathVariable Long postId, @RequestBody PostCommentDto commentDTO) {
        forumService.updatePostComment(postId, commentDTO);
    }

    @PostMapping("/post/diff-merge")
    public void diffMerge(@RequestBody PostDto[] posts) {
        forumService.diffMerge(posts[0], posts[1]);
    }
}
