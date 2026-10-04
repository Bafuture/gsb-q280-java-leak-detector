package com.example.gsb.leakdetector;

import java.time.Duration;
import java.util.Set;

/**
 * Rules for leak detection: which resource types are allowed to grow without
 * being reported as leaks, and how long to wait for delayed reclamation
 * before finalizing the verdict.
 */
public record LeakPolicy(Set<ResourceType> allowedGrowth, Duration tolerance) {

    public LeakPolicy {
        allowedGrowth = allowedGrowth == null ? Set.of() : Set.copyOf(allowedGrowth);
        tolerance = tolerance == null ? Duration.ZERO : tolerance;
    }

    /** No growth allowed anywhere; no delayed-reclamation grace period. */
    public static LeakPolicy strict() {
        return new LeakPolicy(Set.of(), Duration.ZERO);
    }

    public boolean allowsGrowth(ResourceType type) {
        return allowedGrowth.contains(type);
    }
}
