package com.vladmihalcea.phd.jdbc.config;

import com.vladmihalcea.phd.common.config.SpringDataSourceTransactionManagerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
@Configuration
@EnableTransactionManagement
@ComponentScan(basePackages = {
    "com.vladmihalcea.phd.jdbc",
    "com.vladmihalcea.phd.common.service"
})
public class JdbcConfiguration extends SpringDataSourceTransactionManagerConfiguration {

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}



