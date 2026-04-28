package com.vladmihalcea.phd.orm.dto2entity.jmh;

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
 * JUnit 5 test that executes the {@link SpringDataJPAForumRestBenchmark} via the JMH
 * programmatic API and asserts that the average response time of each
 * scenario does not exceed the defined thresholds.
 *
 * <p>Warm-up is intentionally short (10 × 10 s) and measurement is kept to
 * 15 × 10 s so the test completes in a reasonable CI time-budget.  Thresholds
 * are intentionally generous (500 ms) to accommodate TestContainers start-up
 * variance; tighten them for production performance gates.
 *
 * @author Vlad Mihalcea
 */
public class SpringDataJPAForumRestBenchmarkTest {

    /** Maximum acceptable average response time per benchmark method (ms). */
    private static final double MAX_AVG_RESPONSE_TIME_MS = 500.0;

    @Test
    public void testDtoToEntitySyncResponseTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkDtoToEntitySync"
        );

        assertMaxAverageTime(results, MAX_AVG_RESPONSE_TIME_MS);
    }

    @Test
    public void testRemoveParentPostCommentResponseTime() throws RunnerException {
        Collection<RunResult> results = runBenchmark(
            "benchmarkRemoveParentPostComment"
        );

        assertMaxAverageTime(results, MAX_AVG_RESPONSE_TIME_MS);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Collection<RunResult> runBenchmark(String methodName) throws RunnerException {
        Options opts = new OptionsBuilder()
            .include(SpringDataJPAForumRestBenchmark.class.getSimpleName() + "\\." + methodName)
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
                    "Benchmark [%s] average response time %.2f ms exceeds threshold of %.0f ms",
                    label, meanMs, maxMs
                )
            );
        }
    }
}


