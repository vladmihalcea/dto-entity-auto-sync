package com.vladmihalcea.phd.jdbc.controller;

import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.common.service.ForumService;
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

    @PostMapping("/post")
    public void updatePost(@RequestBody PostDto postDTO) {
        forumService.updatePost(postDTO);
    }
}
