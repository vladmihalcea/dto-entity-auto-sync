package com.vladmihalcea.phd.common.util.database;

import com.vladmihalcea.phd.common.util.database.DatabaseGuard.RunProcess;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the {@code benchmark_run} table lets one run use the database at a time: a second run fails fast
 * while the first one is started, the run of a killed process becomes interrupted, and a completed run lets the
 * next one start. The tests that change the runs roll their changes back, so the run of this JVM stays started.
 *
 * @author Vlad Mihalcea
 */
public class DatabaseGuardTest {

    private final DataSourceProvider provider = Database.POSTGRESQL.dataSourceProvider();

    /**
     * Preparing the database registers the run of this JVM, unless another test has prepared it already.
     */
    private final DataSource dataSource = provider.dataSource();

    @Test
    public void testSecondRunFailsFast() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> DatabaseGuard.register(connection, provider.url(), PostgreSQLDataSourceProvider.MODULE, RunProcess.current())
            );
            assertTrue(e.getMessage().startsWith("Another benchmark or test process is using the database"), e.getMessage());
        }
    }

    @Test
    public void testRunOfAKilledProcessBecomesInterrupted() throws SQLException, IOException, InterruptedException {
        Process process = new ProcessBuilder(ProcessHandle.current().info().command().orElseThrow(), "-version")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        process.waitFor();
        // As if the started run belonged to that process, which has exited
        assertRunOfAGoneProcessBecomesInterrupted("UPDATE benchmark_run SET pid = " + process.pid() + ", process_started_at = NULL WHERE id = ?");
    }

    @Test
    public void testRunOfAProcessWhosePidWasReusedBecomesInterrupted() throws SQLException {
        // As if this JVM had reused the PID of the process of the started run
        assertRunOfAGoneProcessBecomesInterrupted("UPDATE benchmark_run SET process_started_at = '2000-01-01T00:00:00Z' WHERE id = ?");
    }

    @Test
    public void testCompletedRunLetsTheNextOneStart() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long run = startedRun(connection);
                DatabaseGuard.complete(connection, run);
                assertEquals("completed", status(connection, run));

                long nextRun = DatabaseGuard.register(connection, provider.url(), PostgreSQLDataSourceProvider.MODULE, RunProcess.current());
                assertEquals(nextRun, startedRun(connection));
            } finally {
                connection.rollback();
            }
        }
    }

    private void assertRunOfAGoneProcessBecomesInterrupted(String update) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long run = startedRun(connection);
                try (PreparedStatement statement = connection.prepareStatement(update)) {
                    statement.setLong(1, run);
                    assertEquals(1, statement.executeUpdate());
                }

                long nextRun = DatabaseGuard.register(connection, provider.url(), PostgreSQLDataSourceProvider.MODULE, RunProcess.current());
                assertEquals("interrupted", status(connection, run));
                assertEquals(nextRun, startedRun(connection));
            } finally {
                connection.rollback();
            }
        }
    }

    /**
     * @return the started run, which is the run of this JVM
     */
    private static long startedRun(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT id, pid, ended_at FROM benchmark_run WHERE status = 'started'")) {
            assertTrue(resultSet.next(), "a started run");
            assertEquals(ProcessHandle.current().pid(), resultSet.getLong(2), "the run of this JVM");
            assertNull(resultSet.getObject(3, OffsetDateTime.class));
            long run = resultSet.getLong(1);
            assertFalse(resultSet.next(), "one started run");
            return run;
        }
    }

    private static String status(Connection connection, long run) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT status, ended_at FROM benchmark_run WHERE id = ?")) {
            statement.setLong(1, run);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "run " + run);
                assertNotNull(resultSet.getObject(2, OffsetDateTime.class), "an end time");
                return resultSet.getString(1);
            }
        }
    }
}
