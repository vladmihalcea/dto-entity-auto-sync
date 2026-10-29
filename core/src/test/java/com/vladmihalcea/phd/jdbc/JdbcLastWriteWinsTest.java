package com.vladmihalcea.phd.jdbc;

import com.vladmihalcea.phd.common.AbstractLastWriteWinsTest;
import com.vladmihalcea.phd.jdbc.config.JdbcConfiguration;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JdbcConfiguration.class)
public class JdbcLastWriteWinsTest extends AbstractLastWriteWinsTest {
}
