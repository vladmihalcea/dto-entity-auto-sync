package com.vladmihalcea.phd.controller;

import com.vladmihalcea.phd.dto.PostDto;
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
}
