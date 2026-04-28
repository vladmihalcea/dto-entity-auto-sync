package com.vladmihalcea.phd.common.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Adds Spring MVC support on top of the base JPA test configuration so that
 * an embedded web server can be started for full HTTP integration tests.
 *
 * @author Vlad Mihalcea
 */
@TestConfiguration
@EnableWebMvc
public class SpringWebConfiguration {
}


