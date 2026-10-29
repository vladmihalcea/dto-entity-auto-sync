package com.vladmihalcea.phd.common.util.database;

import org.hibernate.dialect.PostgreSQLDialect;
import org.postgresql.Driver;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLDataSourceProvider extends AbstractContainerDataSourceProvider {

    /**
     * Each module uses a separate database, since the db_schema.sql scripts of the modules create different tables,
     * e.g., the posts table of the versioning module has a version column.
     */
    public static final String MODULE = "versioning";
    public static final String DATABASE = "dto2entity_versioning";

    /*
     * The connection settings can point to a remote PostgreSQL server, e.g.:
     * -Djdbc.url=jdbc:postgresql://192.168.1.20:5432/dto2entity_versioning, or the JDBC_URL environment variable.
     */
    private static final String URL = setting("jdbc.url", "JDBC_URL");
    private static final String USER = setting("jdbc.user", "JDBC_USER");
    private static final String PASSWORD = setting("jdbc.password", "JDBC_PASSWORD");

    @Override
    public String hibernateDialect() {
        return PostgreSQLDialect.class.getName();
    }

    @Override
    protected boolean isConfigured() {
        return URL != null;
    }

    @Override
    protected String module() {
        return MODULE;
    }

    @Override
    protected String defaultJdbcUrl() {
        return URL != null ? URL : "jdbc:postgresql://localhost/" + DATABASE;
    }

    protected DataSource newDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(url());
        dataSource.setUser(username());
        dataSource.setPassword(password());

        return dataSource;
    }

    @Override
    public Class<? extends DataSource> dataSourceClassName() {
        return PGSimpleDataSource.class;
    }

    @Override
    public Class driverClassName() {
        return Driver.class;
    }

    @Override
    public Properties dataSourceProperties() {
        Properties properties = new Properties();
        properties.setProperty("databaseName", DATABASE);
        properties.setProperty("serverName", "localhost");
        properties.setProperty("user", username());
        properties.setProperty("password", password());
        return properties;
    }

    @Override
    public String username() {
        return USER != null ? USER : "postgres";
    }

    @Override
    public String password() {
        return PASSWORD != null ? PASSWORD : "admin";
    }

    @Override
    public Database database() {
        return Database.POSTGRESQL;
    }
}
