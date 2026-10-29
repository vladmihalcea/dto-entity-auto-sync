package com.vladmihalcea.phd.common.util;

import org.openjdk.jmh.infra.ThreadParams;

/**
 * Hands out the post IDs of one benchmark thread. Thread {@code i} of {@code n} uses the posts
 * from {@code 1 + i * size} to {@code (i + 1) * size}, where {@code size = postCount / n},
 * so concurrent threads never edit the same post.
 *
 * @author Vlad Mihalcea
 */
public class PostRange {

    private final int threadIndex;
    private final long firstPostId;
    private final int size;
    private int next;

    public PostRange(ThreadParams threadParams, int postCount) {
        threadIndex = threadParams.getThreadIndex();
        size = postCount / threadParams.getThreadCount();
        firstPostId = 1 + (long) threadIndex * size;
    }

    /**
     * Starts the range over, after the data set was reloaded for the next iteration.
     */
    public void reset() {
        next = 0;
    }

    /**
     * @param reuse {@code true} to start the range over when it is exhausted, which only read benchmarks can do,
     *              since the edits remove the post's "JPA" tag and a second edit of the same post fails
     * @return the ID of the next post
     */
    public long nextPostId(boolean reuse) {
        if (next == size) {
            if (!reuse) {
                throw new IllegalStateException(String.format(
                    "Benchmark thread %d edited all %d posts of its range within one iteration, and a post cannot be edited twice. " +
                    "Use fewer threads, shorter iterations, or a larger data set.",
                    threadIndex,
                    size
                ));
            }
            next = 0;
        }
        return firstPostId + next++;
    }
}
