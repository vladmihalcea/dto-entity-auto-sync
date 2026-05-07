package com.vladmihalcea.test;

import com.vladmihalcea.phd.app.ForumApplication;
import com.vladmihalcea.test.config.BenchmarkTestData;
import com.vladmihalcea.test.config.SpringWebConfiguration;
import com.vladmihalcea.test.config.JdbcConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;

/**
 * @author Vlad Mihalcea
 */
@SpringBootTest(
    classes = {
        JdbcConfiguration.class,
        SpringWebConfiguration.class,
        ForumApplication.class,
        BenchmarkTestData.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class JdbcRestTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private BenchmarkTestData testData;

    private final RestCaller restCaller;

    public JdbcRestTest(@LocalServerPort int port) {
        this.restCaller = new RestCaller(
            port,
            new RestTemplate()
        );
    }

    @BeforeEach
    public void init() {
        if(!testData.isDataValid()) {
            testData.createTestData();
        }
    }

    @Test
    public void testLoadPostDtoWithMultiset() {
        String postDto = restCaller.loadPostJsonString(1L);
        LOGGER.info("Post: {}", postDto);
    }
}