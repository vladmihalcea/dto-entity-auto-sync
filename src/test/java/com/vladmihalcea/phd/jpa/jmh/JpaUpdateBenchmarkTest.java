package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.common.util.AbstractBenchmarkTest;

/**
 * @author Vlad Mihalcea
 */
public class JpaUpdateBenchmarkTest extends AbstractBenchmarkTest {

    @Override
    protected Class benchmarkClass() {
        return JpaUpdateBenchmark.class;
    }
}