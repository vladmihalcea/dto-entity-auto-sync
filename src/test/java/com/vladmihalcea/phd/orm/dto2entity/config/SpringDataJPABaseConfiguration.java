package com.vladmihalcea.phd.orm.dto2entity.config;

import com.vladmihalcea.phd.orm.dto2entity.dto.PostDTO;
import com.vladmihalcea.phd.orm.util.database.DataSourceProvider;
import com.vladmihalcea.phd.orm.util.logging.DataSourceProxyType;
import com.vladmihalcea.phd.orm.util.database.Database;
import com.vladmihalcea.phd.orm.util.logging.InlineQueryLogEntryCreator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;
import jakarta.persistence.EntityManagerFactory;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 *
 * @author Vlad Mihalcea
 */
@Configuration
@EnableTransactionManagement
@EnableAspectJAutoProxy
public abstract class SpringDataJPABaseConfiguration {

    public static final String DATA_SOURCE_PROXY_NAME = DataSourceProxyType.DATA_SOURCE_PROXY.name();

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

    protected Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        properties.put(
            "hibernate.integrator_provider",
            (IntegratorProvider) () -> Collections.singletonList(
                new ClassImportIntegrator(Arrays.asList(PostDTO.class))
            )
        );
        additionalProperties(properties);
        return properties;
    }

    protected void additionalProperties(Properties properties) {
        properties.setProperty(AvailableSettings.STATEMENT_BATCH_SIZE, "10");
    }

    protected String[] packagesToScan() {
        return new String[]{
            packageToScan()
        };
    }

    protected String packageToScan() {
        return "com.vladmihalcea.hpjp.hibernate.forum";
    }
}
