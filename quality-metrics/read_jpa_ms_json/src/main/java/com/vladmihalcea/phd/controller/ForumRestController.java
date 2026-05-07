package com.vladmihalcea.phd.controller;

import com.vladmihalcea.phd.service.ForumService;
import com.vladmihalcea.phd.util.JsonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 *
 * @author Vlad Mihalcea
 */
@RestController
public class ForumRestController {

    @Autowired
    private ForumService forumService;

    @GetMapping(path = "/postMultiset", produces = "application/json")
    public @ResponseBody JsonNode findByIdUsingMultiset(
        @RequestParam("postId") Long postId) {
        return JsonUtil.JSON_MAPPER.valueToTree(forumService.loadPostDTOUsingMultiset(postId));
    }
}
