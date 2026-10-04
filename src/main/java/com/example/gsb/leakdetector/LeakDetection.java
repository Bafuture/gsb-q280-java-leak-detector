package com.example.gsb.leakdetector;

import java.util.Objects;

/**
 * Global entry point used by the JUnit extension and by code under test.
 * Holds the shared registry, the (replaceable) resource provider and the
 * run-wide statistics.
 */
public final class LeakDetection {

    private static final ResourceRegistry REGISTRY = new ResourceRegistry();
    private static final LeakStatistics STATISTICS = new LeakStatistics();

    private static volatile ResourceProvider provider = new RegistryBackedProvider(REGISTRY);

    private LeakDetection() {
    }

    /** Shared registry; code under test registers/releases resources here. */
    public static ResourceRegistry registry() {
        return REGISTRY;
    }

    /** Run-wide statistics collected by the JUnit extension. */
    public static LeakStatistics statistics() {
        return STATISTICS;
    }

    public static ResourceProvider provider() {
        return provider;
    }

    /** Replaces the resource provider (e.g. with a simulation/fake). */
    public static void setProvider(ResourceProvider replacement) {
        provider = Objects.requireNonNull(replacement, "replacement");
    }

    /** Restores the default registry-backed provider. */
    public static void resetProvider() {
        provider = new RegistryBackedProvider(REGISTRY);
    }

    /** Creates a detector bound to the currently configured provider. */
    public static LeakDetector detector() {
        return new LeakDetector(provider);
    }
}
