package com.vladmihalcea.phd.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Adds Spring MVC support on top of the base JPA test configuration so that
 * an embedded web server can be started for full HTTP integration tests.
 *
 * @author Vlad Mihalcea
 */
@TestConfiguration
@EnableWebMvc
@ComponentScan(basePackages = {
    "com.vladmihalcea.phd.common.controller"
})
public class SpringWebConfiguration {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


