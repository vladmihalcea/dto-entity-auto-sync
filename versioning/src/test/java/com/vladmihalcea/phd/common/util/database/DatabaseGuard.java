package com.vladmihalcea.phd.common.util.database;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Prepares the database for the current JVM, before Hibernate validates the JPA mapping against it:
 * <ul>
 *     <li>The {@code benchmark_module} table records the module that owns the database, since the schemas of
 *     the modules differ, e.g., in the post version of the versioning module.</li>
 *     <li>The {@code benchmark_run} table records every JVM that used the database, so that a second benchmark
 *     or test process fails fast, instead of truncating and reloading the tables while the first one still runs.
 *     A run is {@code started} until its JVM exits and marks it {@code completed}. A killed JVM cannot mark its run,
 *     so the next run on the same host marks it {@code interrupted} once no process with its PID and start time runs.</li>
 *     <li>{@link DatabaseSchema} creates the tables of the module from its {@code db_schema.sql} script, unless
 *     the database already holds them.</li>
 *     <li>The log of every JMH fork records its run, the server version, and the settings that the paper names.</li>
 * </ul>
 * It keeps no connection open: it closes its connection before returning, and the shutdown hook that completes
 * the run opens a new one.
 *
 * @author Vlad Mihalcea
 */
public final class DatabaseGuard {

    /**
     * Every JVM that used the database, of which at most one is {@code started}, as the unique index ensures.
     * The {@code ended_at} time of an interrupted run is the time when the next run found its process gone.
     */
    private static final String CREATE_RUN_TABLE = """
        CREATE TABLE IF NOT EXISTS benchmark_run (
            id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
            module varchar(32) NOT NULL,
            host varchar(255) NOT NULL,
            pid bigint NOT NULL,
            process_started_at timestamptz,
            status varchar(16) NOT NULL CHECK (status IN ('started', 'completed', 'interrupted')),
            started_at timestamptz NOT NULL DEFAULT now(),
            ended_at timestamptz
        )""";

    private static final String CREATE_RUN_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS benchmark_run_started ON benchmark_run (status) WHERE status = 'started'";

    /**
     * The largest difference between two readings of the start time of the same process,
     * e.g., after the database rounded it to microseconds.
     */
    private static final Duration PROCESS_START_TOLERANCE = Duration.ofSeconds(1);

    /**
     * The host of a process whose host name cannot be resolved, which no run can therefore find gone.
     */
    private static final String UNKNOWN_HOST = "unknown host";

    private static final Map<String, Long> RUNS = new HashMap<>();

    private static final Set<String> PREPARED_URLS = new HashSet<>();

    /**
     * The PostgreSQL settings that the paper names, which every fork log records with the server version.
     */
    private static final String[] SETTINGS = {
        "shared_buffers", "effective_cache_size", "work_mem", "maintenance_work_mem", "max_connections",
        "synchronous_commit", "fsync", "wal_sync_method", "jit", "random_page_cost"
    };

    private DatabaseGuard() {
    }

    /**
     * The process of a run.
     *
     * @param startedAt the start time of the process, or {@code null} if the operating system does not report it
     */
    record RunProcess(String host, long pid, Instant startedAt) {

        static RunProcess current() {
            ProcessHandle process = ProcessHandle.current();
            return new RunProcess(hostName(), process.pid(), process.info().startInstant().orElse(null));
        }

        /**
         * @return {@code true} if the process ran on this host and no process with its PID and start time runs,
         * since a later process can reuse the PID of a killed one; {@code false} if it may still run,
         * which is all that a run on another host can tell
         */
        boolean isGone() {
            if (host.equals(UNKNOWN_HOST) || !host.equalsIgnoreCase(hostName())) {
                return false;
            }
            Optional<ProcessHandle> process = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
            if (process.isEmpty()) {
                return true;
            }
            Optional<Instant> processStartedAt = process.get().info().startInstant();
            return startedAt != null && processStartedAt.isPresent() &&
                Duration.between(processStartedAt.get(), startedAt).abs().compareTo(PROCESS_START_TOLERANCE) > 0;
        }
    }

    private record StartedRun(long id, String module, RunProcess process, OffsetDateTime startedAt) {
    }

    /**
     * Prepares the database for the given module, unless this JVM has already prepared it.
     *
     * @throws IllegalStateException if another run uses the database, if another module owns it,
     *                               or if it cannot be prepared
     */
    public static synchronized void prepare(DataSource dataSource, String url, String module) {
        if (PREPARED_URLS.contains(url)) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            claim(connection, url, module);
            Long runId = RUNS.get(url);
            if (runId == null) {
                long id = register(connection, url, module, RunProcess.current());
                Runtime.getRuntime().addShutdownHook(new Thread(() -> complete(dataSource, url, id), "benchmark-run-" + id));
                RUNS.put(url, id);
                runId = id;
            }
            DatabaseSchema.ensure(connection, url, module);
            describe(connection, url, module, runId);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot prepare the database: " + url, e);
        }
        PREPARED_URLS.add(url);
    }

    /**
     * Registers the given process as the started run of the database. If another run is started,
     * it fails fast, unless the process of that run is gone, in which case that run becomes interrupted.
     *
     * @return the ID of the run
     * @throws IllegalStateException if another run is started and its process may still run
     */
    static long register(Connection connection, String url, String module, RunProcess process) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_RUN_TABLE);
            statement.execute(CREATE_RUN_INDEX);
        }
        // The unique index rejects a second started run, also if two processes start at the same time
        for (int attempt = 0; attempt < 3; attempt++) {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO benchmark_run (module, host, pid, process_started_at, status) VALUES (?, ?, ?, ?, 'started') " +
                "ON CONFLICT DO NOTHING RETURNING id"
            )) {
                statement.setString(1, module);
                statement.setString(2, process.host());
                statement.setLong(3, process.pid());
                if (process.startedAt() != null) {
                    statement.setObject(4, process.startedAt().atOffset(ZoneOffset.UTC));
                } else {
                    statement.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong(1);
                    }
                }
            }
            StartedRun started = startedRun(connection);
            if (started == null) {
                // The started run has just ended
                continue;
            }
            if (!started.process().isGone()) {
                throw new IllegalStateException(String.format(
                    "Another benchmark or test process is using the database %s: run %d of the %s module, " +
                    "pid %d on %s, started at %s. Wait for it to finish, or stop it. If that process is gone, " +
                    "e.g., because its host restarted, mark its run as interrupted: " +
                    "UPDATE benchmark_run SET status = 'interrupted', ended_at = now() WHERE id = %d",
                    url,
                    started.id(),
                    started.module(),
                    started.process().pid(),
                    started.process().host(),
                    started.startedAt(),
                    started.id()
                ));
            }
            interrupt(connection, started);
        }
        throw new IllegalStateException("Cannot register the run in the database " + url);
    }

    /**
     * Marks the given run as completed, unless it is no longer started.
     */
    static void complete(Connection connection, long runId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE benchmark_run SET status = 'completed', ended_at = now() WHERE id = ? AND status = 'started'"
        )) {
            statement.setQueryTimeout(10);
            statement.setLong(1, runId);
            statement.executeUpdate();
        }
    }

    /**
     * Completes the run when the JVM exits, with a new connection, since the connection pools may be closed by then.
     */
    private static void complete(DataSource dataSource, String url, long runId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            complete(connection, runId);
        } catch (SQLException | RuntimeException e) {
            System.err.printf("Cannot complete the run %d in the database %s, so the next run on this host will mark it as interrupted: %s%n", runId, url, e);
        }
    }

    private static StartedRun startedRun(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT id, module, host, pid, process_started_at, started_at FROM benchmark_run WHERE status = 'started'"
             )) {
            if (!resultSet.next()) {
                return null;
            }
            OffsetDateTime processStartedAt = resultSet.getObject(5, OffsetDateTime.class);
            return new StartedRun(
                resultSet.getLong(1),
                resultSet.getString(2),
                new RunProcess(resultSet.getString(3), resultSet.getLong(4), processStartedAt != null ? processStartedAt.toInstant() : null),
                resultSet.getObject(6, OffsetDateTime.class)
            );
        }
    }

    private static void interrupt(Connection connection, StartedRun run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE benchmark_run SET status = 'interrupted', ended_at = now() WHERE id = ? AND status = 'started'"
        )) {
            statement.setLong(1, run.id());
            if (statement.executeUpdate() == 1) {
                System.out.printf(
                    "The run %d of the %s module, pid %d on %s, started at %s, ended without completing, so it is now interrupted%n",
                    run.id(), run.module(), run.process().pid(), run.process().host(), run.startedAt()
                );
            }
        }
    }

    private static void claim(Connection connection, String url, String module) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS benchmark_module (name varchar(32) PRIMARY KEY)");
            try (ResultSet resultSet = statement.executeQuery("SELECT name FROM benchmark_module")) {
                while (resultSet.next()) {
                    String owner = resultSet.getString(1);
                    if (!owner.equals(module)) {
                        throw new IllegalStateException(String.format(
                            "The database %s belongs to the %s module, so the %s module needs a separate database. " +
                            "Point JDBC_URL to the %s module's database, e.g., %s.",
                            url,
                            owner,
                            module,
                            module,
                            PostgreSQLDataSourceProvider.DATABASE
                        ));
                    }
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO benchmark_module (name) VALUES (?) ON CONFLICT DO NOTHING"
        )) {
            statement.setString(1, module);
            statement.executeUpdate();
        }
    }

    /**
     * Writes the run, the server version, and the settings that the paper names, once per JVM and database,
     * so that the log of every JMH fork shows which database server it measured.
     */
    private static void describe(Connection connection, String url, String module, long runId) throws SQLException {
        StringBuilder description = new StringBuilder();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version()")) {
            resultSet.next();
            description.append(resultSet.getString(1));
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT name, current_setting(name) FROM pg_settings WHERE name = ANY (?) ORDER BY name"
        )) {
            statement.setArray(1, connection.createArrayOf("text", SETTINGS));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    description.append(", ").append(resultSet.getString(1)).append('=').append(resultSet.getString(2));
                }
            }
        }
        System.out.printf("Database of the %s module: %s, run %d, %s%n", module, url, runId, description);
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return UNKNOWN_HOST;
        }
    }
}
