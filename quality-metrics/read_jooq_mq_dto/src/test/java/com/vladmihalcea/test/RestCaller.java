package com.vladmihalcea.test;

import com.vladmihalcea.phd.dto.PostDto;
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

    public PostDto loadPostDTO(Long postId) {
        return restTemplate.getForObject(
            baseUrl() + "/post?postId={postId}",
            PostDto.class,
            postId
        );
    }
}
