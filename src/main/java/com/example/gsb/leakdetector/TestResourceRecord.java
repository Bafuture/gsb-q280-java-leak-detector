package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.Map;

/** Statistics for a single test: resource deltas, leak count and ignored allowed growth. */
public record TestResourceRecord(String testName,
                                 Map<ResourceType, Integer> deltas,
                                 int leakCount,
                                 int ignoredGrowthCount,
                                 Instant recordedAt) {

    public TestResourceRecord {
        deltas = deltas == null ? Map.of() : Map.copyOf(deltas);
        recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    public boolean leaked() {
        return leakCount > 0;
    }
}
