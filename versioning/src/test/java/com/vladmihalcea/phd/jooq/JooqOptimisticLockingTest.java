package com.vladmihalcea.phd.jooq;

import com.vladmihalcea.phd.common.AbstractOptimisticLockingTest;
import com.vladmihalcea.phd.jooq.config.JooqConfiguration;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Vlad Mihalcea
 */
@ContextConfiguration(classes = JooqConfiguration.class)
public class JooqOptimisticLockingTest extends AbstractOptimisticLockingTest {
}
