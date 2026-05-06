package com.vladmihalcea.phd.jpa.jmh;

import com.vladmihalcea.phd.common.util.AbstractBenchmarkTest;

/**
 * @author Vlad Mihalcea
 */
public class JpaLoadBenchmarkTest extends AbstractBenchmarkTest {

    @Override
    protected Class benchmarkClass() {
        return JpaLoadBenchmark.class;
    }
}