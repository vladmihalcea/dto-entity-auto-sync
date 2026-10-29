package com.vladmihalcea.phd.common.util.database;

import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * To test the Docker integration.
 *
 * Check the available PostgreSQL service:
 *
 * sc queryex type=service state=all | find /i "SERVICE_NAME: postgres"
 *
 * net stop postgresql-x64-15
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractContainerDataSourceProvider implements DataSourceProvider {

    /**
     * Resolves a connection setting from the given system property or, otherwise,
     * from the given environment variable, which the JMH forks inherit as well.
     *
     * @return the configured value, or {@code null} if the setting is not configured
     */
    protected static String setting(String property, String environmentVariable) {
        String value = System.getProperty(property);
        return value != null ? value : System.getenv(environmentVariable);
    }

    /**
     * @return {@code true} if the database URL was configured explicitly, for instance to use a remote server
     */
    protected abstract boolean isConfigured();

    /**
     * @return the name of the module whose schema the database holds
     */
    protected abstract String module();

    @Override
    public DataSource dataSource() {
        DataSource dataSource = connectedDataSource();
        DatabaseGuard.prepare(dataSource, url(), module());
        return dataSource;
    }

    private DataSource connectedDataSource() {
        DataSource dataSource = newDataSource();
        try(Connection connection = dataSource.getConnection()) {
            return dataSource;
        } catch (SQLException e) {
            // A benchmark must not silently switch to another database server
            if (isConfigured()) {
                throw new IllegalStateException("Cannot connect to the configured database: " + url(), e);
            }
            Database database = database();
            if(database.getContainer() == null) {
                database.initContainer(username(), password());
            }
            return newDataSource();
        }
    }

    @Override
    public String url() {
        JdbcDatabaseContainer container = database().getContainer();
        return container != null ?
            container.getJdbcUrl() :
            defaultJdbcUrl();
    }

    protected abstract String defaultJdbcUrl();

    protected abstract DataSource newDataSource();
}
