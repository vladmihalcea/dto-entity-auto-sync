package com.vladmihalcea.phd.common.config;

import org.postgresql.PGConnection;
import org.postgresql.copy.PGCopyOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the data set that {@link DataSetGenerator} generates, at the scale that the {@code benchmark.scale}
 * system property or the {@code BENCHMARK_SCALE} environment variable sets (1 by default).
 *
 * @author Vlad Mihalcea
 */
@Component
public class BenchmarkTestData {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    /**
     * Holds the ID of the transaction that loaded or last restored the data set, and the description of the data set.
     * Any later insert or update creates row versions whose xmin is newer than that transaction, and any delete
     * lowers a row count, so {@link #isDataValid()} detects every modification, whether a write benchmark,
     * a test, or a manual session made it.
     */
    private static final String MARKER_TABLE = "benchmark_dataset";

    /**
     * Marks a data set whose edited posts were restored: its rows are the loaded ones, but its physical layout differs,
     * so the read benchmarks reload it.
     */
    private static final String RESTORED = " (restored)";

    private static final List<String> EDITABLE_TABLES = List.of("posts", "tags", "post_tags", "post_comments");

    private static final Map<String, String> SECONDARY_INDEXES = Map.of(
        "idx_post_tags_post_id", "CREATE INDEX idx_post_tags_post_id ON post_tags USING btree (post_id)",
        "idx_post_tags_tag_id", "CREATE INDEX idx_post_tags_tag_id ON post_tags USING btree (tag_id)",
        "idx_post_comments_post_id", "CREATE INDEX idx_post_comments_post_id ON post_comments USING btree (post_id)",
        "idx_post_comments_parent_id", "CREATE INDEX idx_post_comments_parent_id ON post_comments USING btree (parent_id)",
        "idx_post_comments_author_id", "CREATE INDEX idx_post_comments_author_id ON post_comments USING btree (author_id)",
        "idx_posts_author_id", "CREATE INDEX idx_posts_author_id ON posts USING btree (author_id)"
    );

    /**
     * The constraints of {@code db_schema.sql} by which a reply must belong to the post of its parent comment, so that
     * deleting the replies of a comment cannot reach another post. Like the secondary indexes, the load drops them
     * before the COPY, the foreign key first, since it depends on the unique constraint, and adds them after it.
     */
    private static final List<String> SAME_POST_PARENT_CONSTRAINTS = List.of(
        "ALTER TABLE post_comments ADD CONSTRAINT uk_post_comments_id_post_id UNIQUE (id, post_id)",
        "ALTER TABLE post_comments ADD CONSTRAINT fk_post_comments_parent_id FOREIGN KEY (parent_id, post_id) REFERENCES post_comments (id, post_id)"
    );

    private static final List<String> DROP_SAME_POST_PARENT_CONSTRAINTS = List.of(
        "ALTER TABLE post_comments DROP CONSTRAINT IF EXISTS fk_post_comments_parent_id",
        "ALTER TABLE post_comments DROP CONSTRAINT IF EXISTS uk_post_comments_id_post_id"
    );

    private static final int COPY_BUFFER_SIZE = 1 << 16;

    private final DataSetGenerator generator = new DataSetGenerator(scale());

    private boolean loaded;

    private Boolean superuser;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    public static int scale() {
        String scale = System.getProperty("benchmark.scale", System.getenv("BENCHMARK_SCALE"));
        return scale != null ? Integer.parseInt(scale) : 1;
    }

    public DataSetGenerator generator() {
        return generator;
    }

    public void createTestData() {
        // The benchmarks never change the users, so an unchanged users table survives the reload
        boolean keepUsers = usersUnchanged();
        truncate(keepUsers);
        LOGGER.info("Load the data set: {}", generator.description());
        transactionTemplate.execute(status -> {
            try {
                if (isSuperuser()) {
                    // The generated rows are consistent, so the load skips the foreign key triggers, which halves its duration
                    jdbcTemplate.execute("SET LOCAL session_replication_role = replica");
                }
                // Building the secondary indexes after the load is faster than maintaining them row by row
                for (String dropConstraint : DROP_SAME_POST_PARENT_CONSTRAINTS) {
                    jdbcTemplate.execute(dropConstraint);
                }
                for (String index : SECONDARY_INDEXES.keySet()) {
                    jdbcTemplate.execute("DROP INDEX IF EXISTS " + index);
                }
                PGConnection connection = DataSourceUtils.getConnection(dataSource).unwrap(PGConnection.class);
                for (String table : DataSetGenerator.TABLES) {
                    if (keepUsers && table.equals("users")) {
                        continue;
                    }
                    LOGGER.info("Copy data to table: {}", table);
                    try (Writer writer = copyWriter(connection, table)) {
                        generator.write(table, writer);
                    }
                }
                for (String createIndex : SECONDARY_INDEXES.values()) {
                    jdbcTemplate.execute(createIndex);
                }
                for (String addConstraint : SAME_POST_PARENT_CONSTRAINTS) {
                    jdbcTemplate.execute(addConstraint);
                }
                // The write benchmarks vacuum the edited tables between iterations, so autovacuum, whose vacuum and
                // analyze would start in the middle of a measurement once an iteration has edited enough rows, is off
                // for them. The statistics of the load stay valid, since every restore reproduces the loaded rows.
                for (String table : EDITABLE_TABLES) {
                    jdbcTemplate.execute(String.format("ALTER TABLE %s SET (autovacuum_enabled = false)", table));
                }
                // The marker commits together with the data set
                jdbcTemplate.update(
                    String.format("INSERT INTO %s (loaded_xid, dataset) SELECT pg_current_xact_id()::text::bigint, ?", MARKER_TABLE),
                    generator.description()
                );
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return null;
        });

        vacuum("VACUUM ANALYZE");
        // The analyze rewrites the rows of pg_statistic, whose autovacuum would otherwise run during the next measurement
        vacuum("VACUUM pg_catalog.pg_statistic");
        loaded = true;
    }

    /**
     * Prepares the data set for the next iteration of a write benchmark. The first call of a fork loads the data set,
     * so every fork starts from the same physical state. Every later call restores only the posts that the previous
     * iteration edited, which takes a fraction of a second at any scale, and falls back to loading the data set
     * if anything else changed.
     */
    public void resetTestData() {
        if (!loaded || !restoreEditedPosts()) {
            createTestData();
        }
    }

    /**
     * Deletes the rows of the posts edited since the marker transaction and inserts them as the data set holds them.
     * The edited posts are the ones with a newer post, comment, or tag link row. Between two iterations of a fork,
     * only the benchmark writes, since the started run in {@code benchmark_run} keeps other processes out and every fork starts
     * by loading the data set, and the benchmark only edits posts of its {@code PostRange}, so no row count check is
     * needed, which would scan every table.
     *
     * @return {@code true} if the data set holds the loaded rows again
     */
    private boolean restoreEditedPosts() {
        Long markerXid = markerXid(generator.description(), generator.description() + RESTORED);
        if (markerXid == null) {
            return false;
        }
        List<Long> editedPostIds = jdbcTemplate.queryForList(String.format("""
            SELECT id FROM posts WHERE age(xmin) < age('%1$d'::xid)
            UNION SELECT post_id FROM post_comments WHERE age(xmin) < age('%1$d'::xid)
            UNION SELECT post_id FROM post_tags WHERE age(xmin) < age('%1$d'::xid)
            ORDER BY 1
            """, markerXid), Long.class);
        if (editedPostIds.isEmpty()) {
            return true;
        }
        if (editedPostIds.getFirst() < 1 || editedPostIds.getLast() > generator.editablePostCount()) {
            LOGGER.info("Rows of posts other than the editable ones changed, so the data set is reloaded");
            return false;
        }
        List<long[]> postIdRanges = ranges(editedPostIds);

        transactionTemplate.execute(status -> {
            try {
                Connection connection = DataSourceUtils.getConnection(dataSource);
                Array postIds = connection.createArrayOf("bigint", editedPostIds.toArray());
                // The tags of the edited posts: the loaded ones, even if a benchmark unlinked them, and the added ones
                List<Long> tagIds = new ArrayList<>(jdbcTemplate.queryForList("SELECT tag_id FROM post_tags WHERE post_id = ANY(?)", Long.class, postIds));
                for (long postId : editedPostIds) {
                    for (long tagId : DataSetGenerator.tagIds(postId)) {
                        tagIds.add(tagId);
                    }
                }
                jdbcTemplate.update("DELETE FROM post_tags WHERE post_id = ANY(?)", postIds);
                jdbcTemplate.update("DELETE FROM post_comments WHERE post_id = ANY(?)", postIds);
                jdbcTemplate.update("DELETE FROM tags WHERE id = ANY(?)", connection.createArrayOf("bigint", tagIds.toArray()));
                jdbcTemplate.update("DELETE FROM posts WHERE id = ANY(?)", postIds);

                PGConnection pgConnection = connection.unwrap(PGConnection.class);
                for (String table : EDITABLE_TABLES) {
                    try (Writer writer = copyWriter(pgConnection, table)) {
                        for (long[] range : postIdRanges) {
                            generator.writeEditablePosts(table, range[0], range[1], writer);
                        }
                    }
                }
                jdbcTemplate.update(
                    String.format("UPDATE %s SET loaded_xid = pg_current_xact_id()::text::bigint, dataset = ?", MARKER_TABLE),
                    generator.description() + RESTORED
                );
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        });

        // Reclaims the deleted rows before the next iteration, since autovacuum is off for these tables
        vacuum("VACUUM " + String.join(", ", EDITABLE_TABLES));
        return true;
    }

    /**
     * @return {@code true} if the tables hold the configured data set exactly as {@link #createTestData()} loaded it
     */
    public boolean isDataValid() {
        Long loadedXid = markerXid(generator.description());
        if (loadedXid == null) {
            return false;
        }
        for (String table : DataSetGenerator.TABLES) {
            if (!isUnmodified(table, loadedXid)) {
                LOGGER.info("The data set was modified, starting with table: {}", table);
                return false;
            }
        }
        return true;
    }

    /**
     * Deletes all rows in dependency order inside a single committed transaction.
     */
    public void deleteAll() {
        truncate(false);
    }

    private void truncate(boolean keepUsers) {
        transactionTemplate.execute(status -> {
            jdbcTemplate.execute(String.format("DROP TABLE IF EXISTS %s", MARKER_TABLE));
            jdbcTemplate.execute(String.format("CREATE TABLE %s (loaded_xid bigint NOT NULL, dataset varchar(200) NOT NULL)", MARKER_TABLE));
            for(String table : DataSetGenerator.TABLES.reversed()) {
                if (keepUsers && table.equals("users")) {
                    continue;
                }
                LOGGER.info("Truncate table: {}", table);
                jdbcTemplate.execute(String.format("TRUNCATE %s CASCADE", table));

            }
            return null;
        });
    }

    private boolean usersUnchanged() {
        Long markerXid = markerXid(generator.description(), generator.description() + RESTORED);
        return markerXid != null && isUnmodified("users", markerXid);
    }

    /**
     * @return the 32-bit transaction ID of the marker if the marker describes one of the given data sets, or {@code null}
     */
    private Long markerXid(String... datasets) {
        Boolean markerExists = jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = 'dataset')",
            Boolean.class,
            MARKER_TABLE
        );
        if (!Boolean.TRUE.equals(markerExists)) {
            return null;
        }
        List<Map<String, Object>> markers = jdbcTemplate.queryForList(String.format("SELECT loaded_xid, dataset FROM %s", MARKER_TABLE));
        if (markers.size() != 1 || !List.of(datasets).contains(markers.getFirst().get("dataset"))) {
            return null;
        }
        // The xmin column holds the 32-bit transaction ID, and age() compares transaction IDs across wraparound
        return ((Number) markers.getFirst().get("loaded_xid")).longValue() % (1L << 32);
    }

    /**
     * @return {@code true} if the table has its generated row count and no row newer than the marker transaction
     */
    private boolean isUnmodified(String table, long markerXid) {
        Map<String, Object> rows = jdbcTemplate.queryForMap(String.format(
            "SELECT count(*) AS row_count, count(*) FILTER (WHERE age(xmin) < age('%d'::xid)) AS modified_count FROM %s",
            markerXid,
            table
        ));
        return ((Number) rows.get("row_count")).longValue() == generator.rowCounts().get(table) &&
            ((Number) rows.get("modified_count")).longValue() == 0;
    }

    private boolean isSuperuser() {
        if (superuser == null) {
            superuser = jdbcTemplate.queryForObject("SELECT rolsuper FROM pg_roles WHERE rolname = current_user", Boolean.class);
        }
        return superuser;
    }

    /**
     * @return the runs of consecutive post IDs, as {@code [first, last]} pairs
     */
    private static List<long[]> ranges(List<Long> sortedPostIds) {
        List<long[]> ranges = new ArrayList<>();
        for (long postId : sortedPostIds) {
            if (!ranges.isEmpty() && ranges.getLast()[1] == postId - 1) {
                ranges.getLast()[1] = postId;
            } else {
                ranges.add(new long[] {postId, postId});
            }
        }
        return ranges;
    }

    /**
     * The COPY lists the columns, so the version column of the versioning module's posts table gets its default,
     * and the generated rows stream into the COPY, so the database server can run on another machine.
     */
    private static Writer copyWriter(PGConnection connection, String table) throws SQLException {
        String copy = String.format("COPY %s (%s) FROM STDIN (FORMAT csv)", table, DataSetGenerator.columns(table));
        return new BufferedWriter(
            new OutputStreamWriter(new PGCopyOutputStream(connection, copy, COPY_BUFFER_SIZE), StandardCharsets.UTF_8),
            COPY_BUFFER_SIZE
        );
    }

    private void vacuum(String command) {
        try(Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement()) {
            connection.setAutoCommit(true);
            statement.executeLargeUpdate(command);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
