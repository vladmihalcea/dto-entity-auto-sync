package com.vladmihalcea.phd.common.config;

import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.vladmihalcea.phd.common.config.DataSetGenerator.FAN_OUT_POST_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that restoring the edited posts between the iterations of a write benchmark
 * produces the rows that loading the data set produces.
 *
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    JdbcConfiguration.class,
    BenchmarkTestData.class,
})
public class BenchmarkTestDataTest {

    @Autowired
    private BenchmarkTestData testData;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    public void testRestoreEditedPosts() {
        testData.createTestData();
        Map<String, String> loadedRows = tableDigests();

        // The kinds of changes that the Multi-Ops and Diff-Merge benchmarks make to the posts they edit
        execute("UPDATE posts SET title = 'Diff Merge DTO Sync Pattern', votes = votes + 1 WHERE id IN (1, 2, 3, 900)");
        execute("DELETE FROM post_comments WHERE id = 2");
        execute("UPDATE post_comments SET votes = votes + 1 WHERE id = 3");
        execute("INSERT INTO post_comments (id, content, votes, author_id, parent_id, post_id) VALUES (900000000000000001, 'Does it work with jOOQ?', 0, 1, 3, 2)");
        execute("DELETE FROM post_tags WHERE post_id = 1 AND tag_id = 2");
        execute("DELETE FROM post_tags WHERE post_id = 2 AND tag_id = 6");
        execute("DELETE FROM tags WHERE id = 6");
        execute("INSERT INTO tags (id, name) VALUES (900000000000000002, 'Jakarta Persistence 900000000000000002')");
        execute("INSERT INTO post_tags (tag_id, post_id) VALUES (900000000000000002, 2)");
        assertFalse(testData.isDataValid());

        testData.resetTestData();

        assertEquals(loadedRows, tableDigests());
        // The rows match, but the physical layout differs, so the read benchmarks reload the data set
        assertFalse(testData.isDataValid());
    }

    @Test
    public void testReloadWhenRestoringIsNotEnough() {
        testData.createTestData();
        Map<String, String> loadedRows = tableDigests();

        // The write benchmarks never edit the fan-out posts, so restoring the editable posts cannot undo this
        execute(String.format("UPDATE posts SET votes = votes + 1 WHERE id = %d", FAN_OUT_POST_ID + 10));

        testData.resetTestData();

        assertEquals(loadedRows, tableDigests());
        assertTrue(testData.isDataValid());
    }

    @Test
    public void testReplyMustBelongToThePostOfItsParent() {
        testData.createTestData();

        // Comment 1 belongs to post 1, and comment 4 is the reply of post 2
        assertThrows(DataIntegrityViolationException.class, () -> execute("UPDATE post_comments SET parent_id = 1 WHERE id = 4"));
        assertThrows(DataIntegrityViolationException.class, () -> execute(
            "INSERT INTO post_comments (id, content, votes, author_id, parent_id, post_id) VALUES (900000000000000001, 'Wrong post', 0, 1, 1, 2)"
        ));
        assertTrue(testData.isDataValid());
    }

    private Map<String, String> tableDigests() {
        Map<String, String> digests = new LinkedHashMap<>();
        for (String table : DataSetGenerator.TABLES) {
            digests.put(table, jdbcTemplate.queryForObject(
                String.format("SELECT md5(string_agg(t::text, ',' ORDER BY t::text)) FROM %s t", table),
                String.class
            ));
        }
        return digests;
    }

    private void execute(String sql) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(sql));
    }
}
