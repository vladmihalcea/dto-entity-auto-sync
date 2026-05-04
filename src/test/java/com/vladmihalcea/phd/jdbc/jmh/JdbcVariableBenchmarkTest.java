package com.vladmihalcea.phd.jdbc.jmh;

import com.vladmihalcea.phd.common.util.AbstractBenchmarkTest;

/**
 * @author Vlad Mihalcea
 */
public class JdbcVariableBenchmarkTest extends AbstractBenchmarkTest {

    @Override
    protected Class benchmarkClass() {
        return JdbcVariableBenchmark.class;
    }
}