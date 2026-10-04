package com.example.gsb.leakdetector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of comparing an "after" snapshot against a baseline. Growth on a
 * disallowed type is a leak; growth on an allowed type is recorded as ignored
 * growth and does not fail the check.
 */
public final class LeakReport {

    private final Map<ResourceType, Integer> leaks;
    private final Map<ResourceType, List<TrackedResource>> leakedResources;
    private final Map<ResourceType, Integer> ignoredGrowth;
    private final int rechecks;

    public LeakReport(Map<ResourceType, Integer> leaks,
                      Map<ResourceType, List<TrackedResource>> leakedResources,
                      Map<ResourceType, Integer> ignoredGrowth,
                      int rechecks) {
        this.leaks = leaks == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(leaks));
        this.leakedResources =
                leakedResources == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(leakedResources));
        this.ignoredGrowth =
                ignoredGrowth == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(ignoredGrowth));
        this.rechecks = rechecks;
    }

    /** Positive growth counts for disallowed resource types. */
    public Map<ResourceType, Integer> leaks() {
        return leaks;
    }

    /** Resource instances that appeared after the baseline, grouped by type. */
    public Map<ResourceType, List<TrackedResource>> leakedResources() {
        return leakedResources;
    }

    /** Positive growth counts for allowed types (intentionally ignored). */
    public Map<ResourceType, Integer> ignoredGrowth() {
        return ignoredGrowth;
    }

    /** Number of extra snapshots taken while waiting for delayed reclamation. */
    public int rechecks() {
        return rechecks;
    }

    public boolean hasLeaks() {
        return !leaks.isEmpty();
    }

    public int totalLeakCount() {
        return leaks.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int totalIgnoredCount() {
        return ignoredGrowth.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Human-readable verdict, including every leaked resource's call site. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (leaks.isEmpty()) {
            builder.append("no leaks");
        } else {
            leaks.forEach((type, count) -> {
                builder.append("  - ").append(type).append(": +").append(count).append(System.lineSeparator());
                leakedResources.getOrDefault(type, List.of()).forEach(resource ->
                        builder.append("      ").append(resource).append(System.lineSeparator()));
            });
        }
        if (!ignoredGrowth.isEmpty()) {
            builder.append("  ignored allowed growth: ").append(ignoredGrowth);
        }
        if (rechecks > 0) {
            builder.append("  (verified after ").append(rechecks).append(" delayed re-check(s))");
        }
        return builder.toString();
    }

    /** Fails (with an AssertionError) when this report contains leaks. */
    public void throwIfLeaked(String context) {
        if (hasLeaks()) {
            throw new LeakDetectionException("Resource leak detected in " + context + System.lineSeparator()
                    + describe());
        }
    }
}
