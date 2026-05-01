package com.vladmihalcea.phd.jooq.controller;

import com.vladmihalcea.phd.common.controller.ForumRestController;
import com.vladmihalcea.phd.jooq.service.JooqForumService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 *
 * @author Vlad Mihalcea
 */
@RestController
public class JooqForumRestController extends ForumRestController {

    @Autowired
    private JooqForumService forumService;

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
}
