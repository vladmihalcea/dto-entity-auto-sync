package com.vladmihalcea.phd.common.util;

import io.hypersistence.tsid.TSID;

/**
 * @author Vlad Mihalcea
 */
public class TimeSortedIdentifierGenerator {

    private static final TSID.Factory THREAD_LOCAL_TSID_FACTORY = TSID.Factory
        .builder()
        .withRandomFunction(TSID.Factory.THREAD_LOCAL_RANDOM_FUNCTION)
        .build();

    public static long generate() {
        return THREAD_LOCAL_TSID_FACTORY.generate().toLong();
    }
}
