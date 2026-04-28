package com.vladmihalcea.phd.common.util.database;

import org.springframework.beans.BeanUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.Collections;

/**
 * @author Vlad Mihalcea
 */
public enum Database {
	//Mandatory databases
	POSTGRESQL {
		@Override
		public Class<? extends DataSourceProvider> dataSourceProviderClass() {
			return PostgreSQLDataSourceProvider.class;
		}

		@Override
		protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
			return new PostgreSQLContainer("postgres:15.3");
		}
	}
	;

	private JdbcDatabaseContainer container;

	public JdbcDatabaseContainer getContainer() {
		return container;
	}

	public DataSourceProvider dataSourceProvider() {
        return BeanUtils.instantiateClass(dataSourceProviderClass());
	}
	
	public abstract Class<? extends DataSourceProvider> dataSourceProviderClass();

	public void initContainer(String username, String password) {
		container = (JdbcDatabaseContainer) newJdbcDatabaseContainer()
			.withReuse(true)
			.withEnv(Collections.singletonMap("ACCEPT_EULA", "Y"))
			.withTmpFs(Collections.singletonMap("/testtmpfs", "rw"));
        container.withDatabaseName(databaseName());
        container.withUsername(username).withPassword(password);
		container.start();
	}

	protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
		throw new UnsupportedOperationException(
			String.format(
				"The [%s] database was not configured to use Testcontainers!",
				name()
			)
		);
	}

	protected String databaseName() {
		return "dto2entity";
	}
}
