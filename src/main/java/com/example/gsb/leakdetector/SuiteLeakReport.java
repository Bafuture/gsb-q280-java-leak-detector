package com.example.gsb.leakdetector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Suite-level verdict: which tests introduced resources that are still alive at suite end. */
public final class SuiteLeakReport {

    private final Map<String, List<TrackedResource>> leaksByTest;
    private final Map<String, Integer> ignoredGrowthByTest;

    public SuiteLeakReport(Map<String, List<TrackedResource>> leaksByTest, Map<String, Integer> ignoredGrowthByTest) {
        this.leaksByTest = leaksByTest == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(leaksByTest));
        this.ignoredGrowthByTest = ignoredGrowthByTest == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(ignoredGrowthByTest));
    }

    /** Tests that introduced resources still alive at suite end, mapped to those resources. */
    public Map<String, List<TrackedResource>> leaksByTest() {
        return leaksByTest;
    }

    public Map<String, Integer> ignoredGrowthByTest() {
        return ignoredGrowthByTest;
    }

    public boolean hasLeaks() {
        return !leaksByTest.isEmpty();
    }

    public Set<String> testsWithLeaks() {
        return leaksByTest.keySet();
    }

    public String describe() {
        if (!hasLeaks()) {
            return "no suite-level leaks";
        }
        StringBuilder builder = new StringBuilder();
        leaksByTest.forEach((test, resources) -> {
            builder.append("  test '").append(test).append("' introduced:")
                    .append(System.lineSeparator());
            resources.forEach(resource -> builder.append("      ")
                    .append(resource)
                    .append(System.lineSeparator()));
        });
        return builder.toString();
    }
}
