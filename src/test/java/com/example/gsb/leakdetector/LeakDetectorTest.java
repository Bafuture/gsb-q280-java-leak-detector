package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers requirements 2 (leak verdict + allowed growth), 3 (call-site location)
 * and 4 (delayed-reclamation tolerance).
 */
class LeakDetectorTest {

    private ResourceRegistry registry;
    private LeakDetector detector;

    @BeforeEach
    void setUp() {
        registry = new ResourceRegistry();
        detector = new LeakDetector(new RegistryBackedProvider(registry));
    }

    @Test
    void unreleasedResourceIsReportedAsLeak() {
        ResourceSnapshot baseline = detector.snapshot();
        registry.register(ResourceType.CONNECTION, "jdbc:mock://db");

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leaks()).containsEntry(ResourceType.CONNECTION, 1);
        assertThat(report.totalLeakCount()).isEqualTo(1);
    }

    @Test
    void releasedResourceIsNotALeak() {
        ResourceSnapshot baseline = detector.snapshot();
        try (ResourceLease lease = registry.lease(ResourceType.FILE_HANDLE, "/tmp/data.bin")) {
            assertThat(lease.resource()).isNotNull();
        }

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.totalLeakCount()).isZero();
    }

    @Test
    void resourcesReleasedBeforeTestEndDoNotLeak() {
        ResourceSnapshot baseline = detector.snapshot();
        TrackedResource connection = registry.register(ResourceType.CONNECTION, "c1");
        registry.release(connection);

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThat(report.hasLeaks()).isFalse();
    }

    @Test
    void allowedGrowthIsExcludedFromLeaksAndCountedAsIgnored() {
        LeakPolicy policy = new LeakPolicy(Set.of(ResourceType.THREAD), Duration.ZERO);
        ResourceSnapshot baseline = detector.snapshot();
        registry.register(ResourceType.THREAD, "pool-worker");
        registry.register(ResourceType.CONNECTION, "jdbc:leaked");

        LeakReport report = detector.check(baseline, policy);

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leaks()).containsOnlyKeys(ResourceType.CONNECTION);
        assertThat(report.ignoredGrowth()).containsEntry(ResourceType.THREAD, 1);
        assertThat(report.totalIgnoredCount()).isEqualTo(1);
    }

    @Test
    void fullyAllowedRunHasNoLeaks() {
        LeakPolicy policy = new LeakPolicy(Set.of(ResourceType.THREAD, ResourceType.CONNECTION), Duration.ZERO);
        ResourceSnapshot baseline = detector.snapshot();
        registry.register(ResourceType.THREAD, "t");
        registry.register(ResourceType.CONNECTION, "c");

        LeakReport report = detector.check(baseline, policy);

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.totalIgnoredCount()).isEqualTo(2);
    }

    @Test
    void leakedResourceCarriesTheCreationCallSite() {
        ResourceSnapshot baseline = detector.snapshot();
        TrackedResource leaked = registry.register(ResourceType.FILE_HANDLE, "/tmp/leftover.tmp");

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThat(report.leakedResources().get(ResourceType.FILE_HANDLE))
                .extracting(TrackedResource::id)
                .containsExactly(leaked.id());
        CallSite callSite = report.leakedResources()
                .get(ResourceType.FILE_HANDLE).get(0).callSite();
        assertThat(callSite.className()).isEqualTo(LeakDetectorTest.class.getName());
        assertThat(callSite.methodName()).isEqualTo("leakedResourceCarriesTheCreationCallSite");
        assertThat(callSite.lineNumber()).isPositive();
        assertThat(report.describe())
                .contains("/tmp/leftover.tmp")
                .contains(LeakDetectorTest.class.getSimpleName())
                .contains("leakedResourceCarriesTheCreationCallSite");
    }

    @Test
    void delayedReleaseWithinToleranceIsNotReportedAsLeak() throws Exception {
        ResourceSnapshot baseline = detector.snapshot();
        ResourceLease lease = registry.lease(ResourceType.FILE_HANDLE, "/tmp/async.tmp");
        Thread reaper = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            lease.close();
        }, "mock-async-closer");
        reaper.start();

        LeakReport report = detector.check(baseline, new LeakPolicy(Set.of(), Duration.ofSeconds(3)));
        reaper.join();

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.rechecks()).isGreaterThan(0);
    }

    @Test
    void delayedReleaseWithoutToleranceIsReported() {
        ResourceSnapshot baseline = detector.snapshot();
        ResourceLease lease = registry.lease(ResourceType.CONNECTION, "slow-close");

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.rechecks()).isZero();
        lease.close();
    }

    @Test
    void toleranceExpiringBeforeReleaseStillReportsTheLeak() throws Exception {
        ResourceSnapshot baseline = detector.snapshot();
        ResourceLease lease = registry.lease(ResourceType.CONNECTION, "too-slow");
        Thread reaper = new Thread(() -> {
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            lease.close();
        });
        reaper.start();

        LeakReport report = detector.check(baseline, new LeakPolicy(Set.of(), Duration.ofMillis(150)));
        reaper.join();

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leaks()).containsKey(ResourceType.CONNECTION);
    }

    @Test
    void throwIfLeakedRaisesAssertionErrorWithDetails() {
        ResourceSnapshot baseline = detector.snapshot();
        registry.register(ResourceType.THREAD, "zombie");

        LeakReport report = detector.check(baseline, LeakPolicy.strict());

        assertThatThrownBy(() -> report.throwIfLeaked("test 'x'"))
                .isInstanceOf(LeakDetectionException.class)
                .hasMessageContaining("test 'x'")
                .hasMessageContaining("THREAD");
    }
}
