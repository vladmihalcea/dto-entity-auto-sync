package com.vladmihalcea.phd.common.util.database;

import org.springframework.beans.BeanUtils;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

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
	},
	//A PostgreSQL service on Aiven, to run the database on another machine than the benchmarks
	AIVEN {
		@Override
		public Class<? extends DataSourceProvider> dataSourceProviderClass() {
			return AivenDataSourceProvider.class;
		}
	}
	;

	/**
	 * The settings of this machine, whose {@code database} entry, e.g., {@code database=AIVEN},
	 * sets the default database of the machine.
	 */
	public static final Path MACHINE_SETTINGS = Path.of(System.getProperty("user.home"), ".dto2entity", "benchmark.properties");

	/**
	 * @return the database of the benchmarks and the tests: the one that the {@code benchmark.database} system
	 * property or the {@code BENCHMARK_DATABASE} environment variable names, e.g., {@code AIVEN} or {@code POSTGRESQL},
	 * otherwise the default of this machine in {@link #MACHINE_SETTINGS}, otherwise the local PostgreSQL server
	 */
	public static Database current() {
		String name = System.getProperty("benchmark.database", System.getenv("BENCHMARK_DATABASE"));
		if (name == null || name.isBlank()) {
			name = machineDefault();
		}
		return name != null && !name.isBlank() ? valueOf(name.trim().toUpperCase()) : POSTGRESQL;
	}

	private static String machineDefault() {
		if (!Files.exists(MACHINE_SETTINGS)) {
			return null;
		}
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(MACHINE_SETTINGS)) {
			properties.load(reader);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot read the machine settings from " + MACHINE_SETTINGS, e);
		}
		return properties.getProperty("database");
	}

	private transient JdbcDatabaseContainer container;

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
		return PostgreSQLDataSourceProvider.DATABASE;
	}
}
