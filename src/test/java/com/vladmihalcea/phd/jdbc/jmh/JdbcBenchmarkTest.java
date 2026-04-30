package com.vladmihalcea.phd.jdbc.jmh;

import com.vladmihalcea.phd.common.util.AbstractBenchmarkTest;

/**
 * @author Vlad Mihalcea
 */
public class JdbcBenchmarkTest extends AbstractBenchmarkTest {

    @Override
    protected Class benchmarkClass() {
        return JdbcBenchmark.class;
    }
}

