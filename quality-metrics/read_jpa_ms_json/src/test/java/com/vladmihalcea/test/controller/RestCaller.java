package com.vladmihalcea.test.controller;

import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;

/**
 * @author Vlad Mihalcea
 */
public class RestCaller {

    private final int port;
    private final RestTemplate restTemplate;

    public RestCaller(int port, RestTemplate restTemplate) {
        this.port = port;
        this.restTemplate = restTemplate;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    public JsonNode loadPostDTOUsingMultiset(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postMultiset?postId={postId}",
            JsonNode.class,
            postId
        );
    }
}
