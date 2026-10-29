package com.vladmihalcea.phd.jooq;

import com.vladmihalcea.phd.common.AbstractLastWriteWinsTest;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JooqConfiguration.class)
public class JooqLastWriteWinsTest extends AbstractLastWriteWinsTest {
}
