package com.vladmihalcea.phd.common.util.database;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;

/**
 * Creates the tables of the module from its {@value #SCRIPT} script, against which Hibernate only validates
 * the JPA mapping ({@code hibernate.hbm2ddl.auto=validate}).
 * <p>
 * The database keeps its tables and the loaded data set between the JMH forks, so the script runs only if the
 * database does not hold the tables of this exact script: the {@code benchmark_module} table records the SHA-256
 * digest of the script that created them, and a missing or different digest, e.g., of a database that the hbm2ddl
 * update of an older version created, drops the tables and runs the script again. Dropping the tables also drops
 * the data set marker of {@code BenchmarkTestData}, so the next benchmark loads the data set again.
 *
 * @author Vlad Mihalcea
 */
final class DatabaseSchema {

    static final String SCRIPT = "db_schema.sql";

    /**
     * The tables of the script, and the data set marker of {@code BenchmarkTestData}, which describes their rows.
     */
    private static final String DROP_TABLES = "DROP TABLE IF EXISTS post_tags, post_comments, posts, tags, users, benchmark_dataset CASCADE";

    private DatabaseSchema() {
    }

    /**
     * Creates the tables from the script unless the database already holds the tables of this script.
     * It drops the tables before it creates them, which is safe since the caller has registered the run of this JVM
     * in {@code benchmark_run}, so no other run uses the database meanwhile.
     */
    static void ensure(Connection connection, String url, String module) throws SQLException {
        // The line endings depend on the Git checkout, so they do not count as a change of the script
        String script = readScript().replace("\r\n", "\n");
        String digest = sha256(script);
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE benchmark_module ADD COLUMN IF NOT EXISTS schema_sha256 char(64)");
        }
        if (digest.equals(currentDigest(connection, module))) {
            return;
        }
        System.out.printf("Creating the tables of the %s module from %s in %s%n", module, SCRIPT, url);
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute(DROP_TABLES);
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
            try (PreparedStatement statement = connection.prepareStatement("UPDATE benchmark_module SET schema_sha256 = ? WHERE name = ?")) {
                statement.setString(1, digest);
                statement.setString(2, module);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private static String currentDigest(Connection connection, String module) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT schema_sha256 FROM benchmark_module WHERE name = ?")) {
            statement.setString(1, module);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }

    private static String readScript() {
        try (InputStream input = DatabaseSchema.class.getClassLoader().getResourceAsStream(SCRIPT)) {
            if (input == null) {
                throw new IllegalStateException("The " + SCRIPT + " script is missing from the classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the " + SCRIPT + " script", e);
        }
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
