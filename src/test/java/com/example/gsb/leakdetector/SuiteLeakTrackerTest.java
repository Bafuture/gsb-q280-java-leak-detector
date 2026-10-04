package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Covers requirement 5: suite-level granularity attributes leaks to the introducing test. */
class SuiteLeakTrackerTest {

    private ResourceRegistry registry;
    private SuiteLeakTracker tracker;

    @BeforeEach
    void setUp() {
        registry = new ResourceRegistry();
        LeakDetector detector = new LeakDetector(new RegistryBackedProvider(registry));
        tracker = new SuiteLeakTracker(detector, LeakPolicy.strict());
    }

    @Test
    void leakIsAttributedToTheTestThatIntroducedIt() {
        tracker.startSuite();

        tracker.afterTest("testA-clean");

        registry.register(ResourceType.CONNECTION, "conn-from-B");
        tracker.afterTest("testB-leaky");

        tracker.afterTest("testC-clean-again");

        SuiteLeakReport report = tracker.finishSuite();
        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.testsWithLeaks()).containsExactly("testB-leaky");
        assertThat(report.leaksByTest().get("testB-leaky"))
                .extracting(TrackedResource::description)
                .containsExactly("conn-from-B");
    }

    @Test
    void cleanSuiteReportsNoLeaks() {
        tracker.startSuite();
        try (ResourceLease lease = registry.lease(ResourceType.THREAD, "t")) {
            tracker.afterTest("uses-but-releases");
        }
        tracker.afterTest("no-op");

        SuiteLeakReport report = tracker.finishSuite();

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.describe()).contains("no suite-level leaks");
    }

    @Test
    void resourcesReleasedByLaterTestsAreNotSuiteLeaks() {
        tracker.startSuite();
        TrackedResource temp = registry.register(ResourceType.FILE_HANDLE, "temp");
        tracker.afterTest("testA-creates");
        registry.release(temp);
        tracker.afterTest("testB-cleans-up");

        SuiteLeakReport report = tracker.finishSuite();

        assertThat(report.hasLeaks()).isFalse();
    }

    @Test
    void allowedGrowthIsTrackedPerTestWithoutFailing() {
        ResourceRegistry localRegistry = new ResourceRegistry();
        LeakDetector detector = new LeakDetector(new RegistryBackedProvider(localRegistry));
        SuiteLeakTracker lenient = new SuiteLeakTracker(
                detector, new LeakPolicy(Set.of(ResourceType.THREAD), Duration.ZERO));

        lenient.startSuite();
        localRegistry.register(ResourceType.THREAD, "pool-thread");
        lenient.afterTest("spawns-thread");

        SuiteLeakReport report = lenient.finishSuite();

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.ignoredGrowthByTest()).containsEntry("spawns-thread", 1);
    }
}
