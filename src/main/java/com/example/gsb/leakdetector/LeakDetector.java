package com.example.gsb.leakdetector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Takes snapshots from a {@link ResourceProvider} and compares them against a
 * baseline. When a leak is found and the policy grants a tolerance window,
 * the detector keeps re-sampling within that window so resources that are
 * merely being reclaimed (e.g. pending GC / async close) are not misreported.
 */
public final class LeakDetector {

    private static final long POLL_INTERVAL_MILLIS = 50;

    private final ResourceProvider provider;

    public LeakDetector(ResourceProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    /** Captures the current resource state. */
    public ResourceSnapshot snapshot() {
        return ResourceSnapshot.of(provider.activeCounts(), provider.activeResources());
    }

    /**
     * Compares the current state against {@code baseline}. If leaks remain and
     * the policy allows a tolerance window, polls until the leaks disappear or
     * the window expires.
     */
    public LeakReport check(ResourceSnapshot baseline, LeakPolicy policy) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(policy, "policy");
        LeakReport report = compare(baseline, snapshot(), policy, 0);
        if (!report.hasLeaks() || policy.tolerance().isZero() || policy.tolerance().isNegative()) {
            return report;
        }
        long deadlineNanos = System.nanoTime() + policy.tolerance().toNanos();
        int rechecks = 0;
        while (System.nanoTime() < deadlineNanos) {
            sleepQuietly(Math.min(POLL_INTERVAL_MILLIS, Math.max(1, policy.tolerance().toMillis())));
            rechecks++;
            report = compare(baseline, snapshot(), policy, rechecks);
            if (!report.hasLeaks()) {
                return report;
            }
        }
        return report;
    }

    /** Pure comparison between two snapshots; exposed for reuse and testing. */
    public static LeakReport compare(ResourceSnapshot baseline, ResourceSnapshot after, LeakPolicy policy, int rechecks) {
        Map<ResourceType, Integer> leaks = new LinkedHashMap<>();
        Map<ResourceType, List<TrackedResource>> leakedResources = new LinkedHashMap<>();
        Map<ResourceType, Integer> ignoredGrowth = new LinkedHashMap<>();
        for (ResourceType type : ResourceType.values()) {
            int growth = after.delta(type, baseline);
            if (growth <= 0) {
                continue;
            }
            if (policy.allowsGrowth(type)) {
                ignoredGrowth.put(type, growth);
            } else {
                leaks.put(type, growth);
                List<TrackedResource> newResources = new ArrayList<>(after.resourcesOf(type));
                Set<TrackedResource> known = baseline.resourcesOf(type);
                newResources.removeIf(known::contains);
                leakedResources.put(type, List.copyOf(newResources));
            }
        }
        return new LeakReport(leaks, leakedResources, ignoredGrowth, rechecks);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
