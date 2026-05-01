package com.vladmihalcea.phd.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Component
public class BenchmarkTestData {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private static final List<String> TABLES = List.of(
        "users",
        "tags",
        "posts",
        "post_tags",
        "post_comments"
    );

    public void createTestData() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        transactionTemplate.execute(status -> {
            try {
                for(String table : TABLES) {
                    LOGGER.info("Copy data to table: {}", table);

                    jdbcTemplate.execute(
                        String.format(
                            "COPY %s FROM '%s' DELIMITER ',' CSV;",
                            table,
                            Paths.get(classLoader.getResource(String.format("data/%s.csv", table)).toURI()).toAbsolutePath()
                        )
                    );
                }
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_post_tags_post_id ON post_tags USING btree (post_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_post_tags_tag_id ON post_tags USING btree (tag_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_post_comments_post_id ON post_comments USING btree (post_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_post_comments_parent_id ON post_comments USING btree (parent_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_post_comments_author_id ON post_comments USING btree (author_id)");
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_posts_author_id ON posts USING btree (author_id)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return null;
        });

        try(Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.executeLargeUpdate("VACUUM ANALYZE");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes all rows in dependency order inside a single committed transaction.
     */
    public void deleteAll() {
        transactionTemplate.execute(status -> {
            for(String table : TABLES.reversed()) {
                LOGGER.info("Truncate table: {}", table);
                jdbcTemplate.execute(String.format("TRUNCATE %s CASCADE", table));

            }
            return null;
        });
    }
}