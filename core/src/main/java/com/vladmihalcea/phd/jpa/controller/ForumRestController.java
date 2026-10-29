package com.vladmihalcea.phd.jpa.controller;

import com.vladmihalcea.phd.common.dto.PostCommentDto;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.dto.TagDto;
import com.vladmihalcea.phd.common.util.JsonUtil;
import com.vladmihalcea.phd.jpa.service.ForumService;
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

    @GetMapping(path = "/postMultiset", produces = "application/json")
    public @ResponseBody JsonNode findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return JsonUtil.JSON_MAPPER.valueToTree(forumService.loadPostDTOUsingMultiset(postId));
    }

    @PutMapping("/post")
    public void updatePost(@RequestBody PostDto postDTO) {
        forumService.updatePost(postDTO);
    }

    /**
     * The per-change endpoints of the Multi-Ops variant (R1-14), with the paths of JDBC and jOOQ, except that the
     * post's own fields are updated with {@code PATCH}, since {@code PUT /post} merges the whole aggregate.
     */
    @PatchMapping("/post")
    public void patchPost(@RequestBody PostDto postDTO) {
        forumService.patchPost(postDTO);
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
}
