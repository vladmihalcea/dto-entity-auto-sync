package com.vladmihalcea.phd.common.util;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractBenchmarkTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final int ITERATION_COUNT = 20;
    private static final int ITERATION_DURATION_SECONDS = 2;

    protected abstract Class benchmarkClass();

    @Test
    public void testExecutionTimes() throws RunnerException {
        runBenchmarks();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Collection<RunResult> runBenchmarks() throws RunnerException {
        Options opts = new OptionsBuilder()
            .include(benchmarkClass().getSimpleName() + "\\.*")
            .warmupIterations(ITERATION_COUNT / 2)
            .warmupTime(TimeValue.seconds(ITERATION_DURATION_SECONDS))
            .measurementIterations(ITERATION_COUNT)
            .measurementTime(TimeValue.seconds(ITERATION_DURATION_SECONDS))
            .timeUnit(TimeUnit.MILLISECONDS)
            .forks(1)
            .shouldFailOnError(true)
            .build();

        return new Runner(opts).run();
    }
}

