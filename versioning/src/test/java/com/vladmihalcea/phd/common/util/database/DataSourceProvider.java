package com.vladmihalcea.phd.common.util.database;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProvider {

	String hibernateDialect();

	DataSource dataSource();

	Class driverClassName();

	Class<? extends DataSource> dataSourceClassName();

	Properties dataSourceProperties();

	String url();

	String username();

	String password();

	Database database();

	/**
	 * @return the most connections of the pool: 32 by default, so that the concurrent benchmark threads never wait for one
	 */
	default int maxPoolSize() {
		return 32;
	}

	/**
	 * @return the idle connections of the pool: 10 by default, so that the cached Spring contexts
	 * of a test JVM stay below the max_connections of PostgreSQL
	 */
	default int minIdle() {
		return 10;
	}
}
