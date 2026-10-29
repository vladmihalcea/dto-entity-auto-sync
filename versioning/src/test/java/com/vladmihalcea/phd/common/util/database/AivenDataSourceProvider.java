package com.vladmihalcea.phd.common.util.database;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Connects to a PostgreSQL service on Aiven, so that the benchmarks and the tests can run against a database
 * on another machine. Select it with {@code -Dbenchmark.database=AIVEN} or the {@code BENCHMARK_DATABASE=AIVEN}
 * environment variable, which the JMH forks inherit.
 * <p>
 * The connection settings come from the {@code AIVEN_HOST}, {@code AIVEN_PORT}, {@code AIVEN_USER}, and
 * {@code AIVEN_PASSWORD} environment variables or, otherwise, from the {@code host}, {@code port}, {@code user},
 * and {@code password} entries of {@code ~/.dto2entity/aiven.properties}, which keeps the credentials out of the
 * repository. Like the local server, the service needs one database per module, {@code dto2entity} and
 * {@code dto2entity_versioning}, and Aiven requires SSL.
 *
 * @author Vlad Mihalcea
 */
public class AivenDataSourceProvider extends PostgreSQLDataSourceProvider {

    public static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".dto2entity", "aiven.properties");

    private static final Properties FILE_SETTINGS = load(SETTINGS_FILE);

    private static Properties load(Path file) {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the Aiven settings from " + file, e);
            }
        }
        return properties;
    }

    private static String aivenSetting(String key) {
        String value = System.getenv("AIVEN_" + key.toUpperCase(Locale.ROOT));
        if (value == null) {
            value = FILE_SETTINGS.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(String.format(
                "The Aiven setting %s is missing: set the AIVEN_%s environment variable or the %s entry of %s",
                key, key.toUpperCase(Locale.ROOT), key, SETTINGS_FILE));
        }
        return value.trim();
    }

    @Override
    protected boolean isConfigured() {
        return true;
    }

    @Override
    protected String defaultJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%s/%s?sslmode=require", aivenSetting("host"), aivenSetting("port"), DATABASE);
    }

    @Override
    public String username() {
        return aivenSetting("user");
    }

    @Override
    public String password() {
        return aivenSetting("password");
    }

    /**
     * The small Aiven plans allow 15 connections, some of which the service keeps for itself.
     */
    @Override
    public int maxPoolSize() {
        return 8;
    }

    @Override
    public int minIdle() {
        return 2;
    }

    @Override
    public Database database() {
        return Database.AIVEN;
    }
}
