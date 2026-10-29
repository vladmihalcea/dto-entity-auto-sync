package com.vladmihalcea.phd.common.config;

import com.vladmihalcea.phd.common.util.database.DataSourceProvider;
import com.vladmihalcea.phd.common.util.database.Database;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.common.timing.LayerTimingConfiguration;
import com.vladmihalcea.phd.common.timing.LayerTimings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.hypersistence.utils.logging.InlineQueryLogEntryCreator;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
@Configuration
@Import(LayerTimingConfiguration.class)
@EnableTransactionManagement
public class SpringDataSourceTransactionManagerConfiguration {

    public static final String DATA_SOURCE_PROXY_NAME = "JDBC_DATA_SOURCE_PROXY";

    @Bean
    public Database database() {
        return Database.current();
    }

    @Bean
    public DataSourceProvider dataSourceProvider() {
        return database().dataSourceProvider();
    }

    public DataSource poolingDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setAutoCommit(false);
        // The provider sizes the pool: by default up to 32 connections, so that the concurrent benchmark threads
        // never wait for one, but only 10 idle ones, so that the cached Spring contexts of a test JVM stay below
        // the max_connections of PostgreSQL
        DataSourceProvider provider = dataSourceProvider();
        hikariConfig.setMaximumPoolSize(provider.maxPoolSize());
        hikariConfig.setMinimumIdle(provider.minIdle());
        hikariConfig.setDataSource(provider.dataSource());
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    public DataSource dataSource() {
        ChainListener listener = new ChainListener();
        SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
        loggingListener.setQueryLogEntryCreator(new InlineQueryLogEntryCreator());
        listener.addListener(loggingListener);
        listener.addListener(new DataSourceQueryCountListener());
        listener.addListener(LayerTimings.queryListener());
        return ProxyDataSourceBuilder
            .create(poolingDataSource())
            .name(DATA_SOURCE_PROXY_NAME)
            .listener(listener)
            .methodListener(LayerTimings.commitListener())
            .build();
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    /**
     * The JDBC and jOOQ code does not use it: it only validates the JPA mapping against the tables that
     * {@code db_schema.sql} creates, and keeps the Spring context of the JDBC and jOOQ benchmarks as it was.
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        @Autowired DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setPersistenceUnitName(getClass().getSimpleName());
        emf.setPackagesToScan(Post.class.getPackageName());
        emf.setDataSource(dataSource);
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);
        Properties props = new Properties();
        props.setProperty("hibernate.hbm2ddl.auto", "validate");
        props.setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, "10");
        emf.setJpaProperties(props);
        return emf;
    }
}



