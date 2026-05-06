package com.vladmihalcea.phd.common.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    private static final int POST_COUNT = 20_005;
    private static final int POST_COMMENT_COUNT = 40_140;
    private static final int TAG_COUNT = 80140;
    private static final int POST_TAG_COUNT = 80140;
    private static final int USER_COUNT = 3;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

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

    public boolean isDataValid() {
        int postCount = ((Number) entityManager.createQuery("select count(*) from Post").getSingleResult()).intValue();
        int postCommentCount = ((Number) entityManager.createQuery("select count(*) from PostComment").getSingleResult()).intValue();
        int tagCount = ((Number) entityManager.createQuery("select count(*) from Tag").getSingleResult()).intValue();
        int userCount = ((Number) entityManager.createQuery("select count(*) from User").getSingleResult()).intValue();
        int postTagsCount = ((Number) entityManager.createNativeQuery("select count(*) from post_tags").getSingleResult()).intValue();

        return postCount == POST_COUNT &&
               postCommentCount == POST_COMMENT_COUNT &&
               tagCount == TAG_COUNT &&
               userCount == USER_COUNT &&
               postTagsCount == POST_TAG_COUNT;
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