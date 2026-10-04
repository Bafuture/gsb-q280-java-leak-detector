package com.example.gsb.leakdetector;

/** Detection granularity: per-test checks, per-suite checks, or both. */
public enum Granularity {
    TEST,
    SUITE,
    TEST_AND_SUITE;

    public boolean includesTest() {
        return this != SUITE;
    }

    public boolean includesSuite() {
        return this != TEST;
    }
}
