package com.vladmihalcea.phd.jpa.controller;

import com.vladmihalcea.phd.common.dto.PostDto;
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
}
