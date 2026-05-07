package com.vladmihalcea.test.util.database;

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
}
