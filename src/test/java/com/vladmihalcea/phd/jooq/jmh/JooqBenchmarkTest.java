package com.vladmihalcea.phd.jooq.jmh;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Junit 6 test that executes the {@link JooqBenchmark} via the
 * JMH programmatic API and asserts that the average execution time of each
 * scenario does not exceed the defined threshold.
 *
 * @author Vlad Mihalcea
 */
public class JooqBenchmarkTest {

    /** Maximum acceptable average execution time per benchmark method (ms). */
    private static final double MAX_AVG_EXECUTION_TIME_MS = 50.0;

    @Test
    public void testLoadDtoExecutionTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkLoadDto"
        );

        assertMaxAverageTime(results, MAX_AVG_EXECUTION_TIME_MS);
    }

    @Test
    public void testMultiOpsDtoSyncExecutionTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkMultiOpsDtoSync"
        );

        assertMaxAverageTime(results, MAX_AVG_EXECUTION_TIME_MS);
    }

    @Test
    public void testRemoveParentPostCommentExecutionTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkRemoveParentPostComment"
        );

        assertMaxAverageTime(results, MAX_AVG_EXECUTION_TIME_MS);
    }

    @Test
    public void testDiffMergeSyncExecutionTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkDiffMergeSync"
        );

        assertMaxAverageTime(results, MAX_AVG_EXECUTION_TIME_MS);
    }

    @Test
    public void testDiffMergeRemoveParentPostCommentExecutionTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkDiffMergeRemoveParentPostComment"
        );

        assertMaxAverageTime(results, MAX_AVG_EXECUTION_TIME_MS);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Collection<RunResult> runBenchmark(String methodName) throws RunnerException {
        Options opts = new OptionsBuilder()
            .include(JooqBenchmark.class.getSimpleName() + "\\." + methodName)
            .warmupIterations(10)
            .warmupTime(TimeValue.seconds(2))
            .measurementIterations(15)
            .measurementTime(TimeValue.seconds(3))
            .timeUnit(TimeUnit.MILLISECONDS)
            .forks(1)
            .shouldFailOnError(true)
            .build();

        return new Runner(opts).run();
    }

    private void assertMaxAverageTime(Collection<RunResult> results, double maxMs) {
        assertTrue(!results.isEmpty(), "No benchmark results were produced");
        for (RunResult result : results) {
            double meanMs = result.getPrimaryResult().getStatistics().getMean();
            String label = result.getPrimaryResult().getLabel();
            assertTrue(
                meanMs < maxMs,
                () -> String.format(
                    "Benchmark [%s] average execution time %.2f ms exceeds threshold of %.0f ms",
                    label, meanMs, maxMs
                )
            );
        }
    }
}

