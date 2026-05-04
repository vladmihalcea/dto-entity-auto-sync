package com.vladmihalcea.phd.jooq.jmh;

import com.vladmihalcea.phd.common.util.AbstractBenchmarkTest;

/**
 * @author Vlad Mihalcea
 */
public class JooqVariableBenchmarkTest extends AbstractBenchmarkTest {

    @Override
    protected Class benchmarkClass() {
        return JooqVariableBenchmark.class;
    }
}