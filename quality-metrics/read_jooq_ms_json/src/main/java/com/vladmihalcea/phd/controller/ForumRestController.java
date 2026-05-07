package com.vladmihalcea.phd.controller;

import com.vladmihalcea.phd.service.ForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Vlad Mihalcea
 */
@RestController
public class ForumRestController {

    @Autowired
    private ForumService forumService;

    @GetMapping("/postJsonString")
    public String findJsonStringByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return forumService.loadPostJsonStringUsingMultiset(postId);
    }
}
