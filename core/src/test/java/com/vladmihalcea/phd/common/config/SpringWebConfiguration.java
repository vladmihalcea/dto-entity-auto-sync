package com.vladmihalcea.phd.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Adds Spring MVC support.
 *
 * @author Vlad Mihalcea
 */
@TestConfiguration
@EnableWebMvc
public class SpringWebConfiguration {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


