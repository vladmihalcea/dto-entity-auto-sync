package com.vladmihalcea.phd.common.util;

import io.hypersistence.tsid.TSID;

/**
 * @author Vlad Mihalcea
 */
public class TimeSortedIdentifierGenerator {

    public static final int NODE_BITS = 8;

    public static long generate() {
        return TSID.Factory.builder()
            .withRandomFunction(TSID.Factory.THREAD_LOCAL_RANDOM_FUNCTION)
            .withNodeBits(NODE_BITS)
            .withNode((int) (Thread.currentThread().threadId() % 2 ^ NODE_BITS))
            .build()
            .generate()
            .toLong();
    }
}
