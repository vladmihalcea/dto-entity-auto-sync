package com.vladmihalcea.phd.common;

import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the run isolation of the benchmark harness: any modification of the data set,
 * whoever makes it, makes the read benchmarks reload it.
 *
 * @author Vlad Mihalcea
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
    JdbcConfiguration.class,
    BenchmarkTestData.class,
})
public class RunIsolationTest {

    @Autowired
    private BenchmarkTestData testData;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    public void testModificationsInvalidateTheDataSet() {
        testData.createTestData();
        assertTrue(testData.isDataValid());

        execute("UPDATE posts SET votes = votes + 1 WHERE id = 1");
        assertFalse(testData.isDataValid());

        testData.createTestData();
        execute("DELETE FROM post_tags WHERE post_id = 1");
        assertFalse(testData.isDataValid());

        testData.createTestData();
        assertTrue(testData.isDataValid());
    }

    private void execute(String sql) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(sql));
    }
}
