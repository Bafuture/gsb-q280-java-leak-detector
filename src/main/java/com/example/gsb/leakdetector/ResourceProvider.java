package com.example.gsb.leakdetector;

import java.util.Map;
import java.util.Set;

/**
 * Replaceable source of resource observations. The default implementation is
 * backed by a {@link ResourceRegistry}; tests can substitute fakes (e.g. a
 * lambda returning fixed counts) to simulate threads, file handles or
 * connections without touching the real OS/JVM facilities.
 */
@FunctionalInterface
public interface ResourceProvider {

    /** Active counts per resource type. Types may be omitted and are treated as zero. */
    Map<ResourceType, Integer> activeCounts();

    /**
     * Currently active resource instances, if the provider tracks them.
     * Providers that only expose counts (no per-instance detail) may keep the
     * default empty set; leaks are still detected, just without call-site info.
     */
    default Set<TrackedResource> activeResources() {
        return Set.of();
    }
}
