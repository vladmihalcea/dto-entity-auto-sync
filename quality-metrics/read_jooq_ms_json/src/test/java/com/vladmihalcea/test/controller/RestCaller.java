package com.vladmihalcea.test.controller;

import org.springframework.web.client.RestTemplate;

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

    public String loadPostJsonString(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/postJsonString?postId={postId}",
            String.class,
            postId
        );
    }
}
