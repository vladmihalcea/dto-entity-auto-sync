package com.vladmihalcea.test.config;

import com.vladmihalcea.phd.domain.Post;
import com.vladmihalcea.test.util.database.DataSourceProvider;
import com.vladmihalcea.test.util.database.Database;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.hypersistence.utils.logging.InlineQueryLogEntryCreator;
import jakarta.persistence.EntityManagerFactory;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
@ComponentScan(
    basePackages = {
        "com.vladmihalcea.phd"
    }
)
@EnableJpaRepositories(
    basePackages = "com.vladmihalcea.phd.dao"
)
public class JpaConfiguration {

    public static final String DATA_SOURCE_PROXY_NAME = "DATA_SOURCE_PROXY";

    @Bean
    public Database database() {
        return Database.POSTGRESQL;
    }

    @Bean
    public DataSourceProvider dataSourceProvider() {
        return database().dataSourceProvider();
    }

    public DataSource poolingDataSource() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setAutoCommit(false);
        hikariConfig.setMinimumIdle(hikariConfig.getMaximumPoolSize());
        hikariConfig.setDataSource(dataSourceProvider().dataSource());
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    public DataSource dataSource() {
        ChainListener listener = new ChainListener();
        SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
        loggingListener.setQueryLogEntryCreator(new InlineQueryLogEntryCreator());
        listener.addListener(loggingListener);
        listener.addListener(new DataSourceQueryCountListener());
        return ProxyDataSourceBuilder
            .create(poolingDataSource())
            .name(DATA_SOURCE_PROXY_NAME)
            .listener(listener)
            .build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
        @Autowired DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean entityManagerFactoryBean = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactoryBean.setPersistenceUnitName(getClass().getSimpleName());
        entityManagerFactoryBean.setPackagesToScan(packagesToScan());
        entityManagerFactoryBean.setDataSource(dataSource);

        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        entityManagerFactoryBean.setJpaVendorAdapter(vendorAdapter);
        entityManagerFactoryBean.setJpaProperties(properties());
        return entityManagerFactoryBean;
    }

    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory){
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }

    @Bean
    public TransactionTemplate transactionTemplate(EntityManagerFactory entityManagerFactory) {
        return new TransactionTemplate(transactionManager(entityManagerFactory));
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    protected Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "update");
        additionalProperties(properties);
        return properties;
    }

    protected void additionalProperties(Properties properties) {
        properties.setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, "10");
        properties.setProperty(AvailableSettings.ORDER_INSERTS, "true");
    }

    protected String[] packagesToScan() {
        return new String[]{
            Post.class.getPackageName()
        };
    }
}
