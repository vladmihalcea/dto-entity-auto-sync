package com.vladmihalcea.phd.jpa.config;

import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.spi.CriteriaBuilderConfiguration;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViews;
import com.blazebit.persistence.view.spi.EntityViewConfiguration;
import com.vladmihalcea.phd.common.util.database.DataSourceProvider;
import com.vladmihalcea.phd.common.util.database.Database;
import com.vladmihalcea.phd.jpa.domain.Post;
import com.vladmihalcea.phd.jpa.view.PostCommentView;
import com.vladmihalcea.phd.jpa.view.PostWithCommentsAndTagsView;
import com.vladmihalcea.phd.jpa.view.TagView;
import com.vladmihalcea.phd.jpa.view.UserView;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import net.ttddyy.dsproxy.listener.ChainListener;
import net.ttddyy.dsproxy.listener.DataSourceQueryCountListener;
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
        "com.vladmihalcea.phd.jpa"
    }
)
@EnableJpaRepositories(
    basePackages = "com.vladmihalcea.phd.jpa.repository"
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
        hikariConfig.setDataSource(dataSourceProvider().dataSource());
        return new HikariDataSource(hikariConfig);
    }

    @Bean
    public DataSource dataSource() {
        ChainListener listener = new ChainListener();
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

    @Bean
    public CriteriaBuilderFactory criteriaBuilderFactory(EntityManagerFactory emf) {
        CriteriaBuilderConfiguration config = Criteria.getDefault();
        return config.createCriteriaBuilderFactory(emf);
    }

    @Bean
    public EntityViewConfiguration entityViewConfiguration() {
        EntityViewConfiguration cfg = EntityViews.createDefaultConfiguration();
        cfg.addEntityView(UserView.class);
        cfg.addEntityView(TagView.class);
        cfg.addEntityView(PostCommentView.class);
        cfg.addEntityView(PostWithCommentsAndTagsView.class);
        return cfg;
    }

    @Bean
    public EntityViewManager entityViewManager(
            CriteriaBuilderFactory cbf,
            EntityViewConfiguration entityViewConfiguration) {
        return entityViewConfiguration.createEntityViewManager(cbf);
    }

    protected String[] packagesToScan() {
        return new String[]{
            Post.class.getPackageName()
        };
    }
}
