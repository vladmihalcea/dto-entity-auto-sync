package com.vladmihalcea.phd.jooq.config;

import com.vladmihalcea.phd.common.config.SpringDataSourceTransactionManagerConfiguration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = "com.vladmihalcea.phd.jooq")
public class JooqDTOManualSyncConfiguration extends SpringDataSourceTransactionManagerConfiguration {

    @Bean
    public DSLContext dslContext(DataSource dataSource) {
        DefaultConfiguration configuration = new DefaultConfiguration();
        configuration.setSQLDialect(SQLDialect.POSTGRES);
        configuration.setDataSource(new TransactionAwareDataSourceProxy(dataSource));
        return new DefaultDSLContext(configuration);
    }
}



