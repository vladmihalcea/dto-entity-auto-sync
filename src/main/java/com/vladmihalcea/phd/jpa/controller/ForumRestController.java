package com.vladmihalcea.phd.jpa.controller;

import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.jpa.service.ForumService;
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

    @GetMapping("/postMultiset")
    public PostDto findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostDTOUsingMultiset(postId);
    }

    @PutMapping("/post")
    public void updatePost(@RequestBody PostDto postDTO) {
        forumService.updatePost(postDTO);
    }
}
