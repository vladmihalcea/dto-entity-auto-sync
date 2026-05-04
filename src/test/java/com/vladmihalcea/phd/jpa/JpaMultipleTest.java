package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.dto.PostDto;
import com.vladmihalcea.phd.jpa.config.JpaConfiguration;
import com.vladmihalcea.phd.jpa.service.ForumService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    JpaConfiguration.class,
    BenchmarkTestData.class,
})
public class JpaMultipleTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private ForumService forumService;

    @Autowired
    private BenchmarkTestData testData;

    @BeforeEach
    public void init() {
        if(!testData.isDataValid()) {
            testData.createTestData();
        }
    }

    @Test
    public void testLoadPostDTOVariableCollections() {
        PostDto postDTO_0 = forumService.loadPostDTO(100_000L);
        assertEquals(0, postDTO_0.getReplies().size());
        assertEquals(0, postDTO_0.getTags().size());

        PostDto postDTO_1 = forumService.loadPostDTO(100_001L);
        assertEquals(1, postDTO_1.getReplies().size());
        assertEquals(1, postDTO_1.getTags().size());

        PostDto postDTO_5 = forumService.loadPostDTO(100_005L);
        assertEquals(5, postDTO_5.getReplies().size());
        assertEquals(5, postDTO_5.getTags().size());

        PostDto postDTO_25 = forumService.loadPostDTO(100_025L);
        assertEquals(25, postDTO_25.getReplies().size());
        assertEquals(25, postDTO_25.getTags().size());

        PostDto postDTO_100 = forumService.loadPostDTO(100_100L);
        assertEquals(100, postDTO_100.getReplies().size());
        assertEquals(100, postDTO_100.getTags().size());
    }

    @Test
    public void testloadPostDTOUsingMultisetVariableCollections() {
        com.vladmihalcea.phd.jpa.view.PostDto postDTO_0 = forumService.loadPostDTOUsingMultiset(100_000L);
        assertEquals(0, postDTO_0.getReplies().size());
        assertEquals(0, postDTO_0.getTags().size());

        com.vladmihalcea.phd.jpa.view.PostDto postDTO_1 = forumService.loadPostDTOUsingMultiset(100_001L);
        assertEquals(1, postDTO_1.getReplies().size());
        assertEquals(1, postDTO_1.getTags().size());

        com.vladmihalcea.phd.jpa.view.PostDto postDTO_5 = forumService.loadPostDTOUsingMultiset(100_005L);
        assertEquals(5, postDTO_5.getReplies().size());
        assertEquals(5, postDTO_5.getTags().size());

        com.vladmihalcea.phd.jpa.view.PostDto postDTO_25 = forumService.loadPostDTOUsingMultiset(100_025L);
        assertEquals(25, postDTO_25.getReplies().size());
        assertEquals(25, postDTO_25.getTags().size());

        com.vladmihalcea.phd.jpa.view.PostDto postDTO_100 = forumService.loadPostDTOUsingMultiset(100_100L);
        assertEquals(100, postDTO_100.getReplies().size());
        assertEquals(100, postDTO_100.getTags().size());
    }
}
