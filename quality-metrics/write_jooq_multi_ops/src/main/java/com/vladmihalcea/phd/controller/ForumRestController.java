package com.vladmihalcea.phd.controller;

import com.vladmihalcea.phd.dto.PostCommentDto;
import com.vladmihalcea.phd.dto.PostDto;
import com.vladmihalcea.phd.dto.TagDto;
import com.vladmihalcea.phd.service.ForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/post/comment/{commentId}")
    public void removePostComment(@PathVariable Long commentId) {
        forumService.removePostComment(commentId);
    }

    @PutMapping("/post/comment")
    public void updatePostComment(@RequestBody PostCommentDto commentDTO) {
        forumService.updatePostComment(commentDTO);
    }
}
