package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Covers requirement 6: per-test statistics, leak counts and ignored allowed growth. */
class LeakStatisticsTest {

    @Test
    void recordsDeltasLeaksAndIgnoredGrowthPerTest() {
        ResourceRegistry registry = new ResourceRegistry();
        LeakDetector detector = new LeakDetector(new RegistryBackedProvider(registry));
        LeakStatistics statistics = new LeakStatistics();

        ResourceSnapshot before = detector.snapshot();
        registry.register(ResourceType.CONNECTION, "leaked-conn");
        registry.register(ResourceType.THREAD, "allowed-thread");
        LeakPolicy policy = new LeakPolicy(Set.of(ResourceType.THREAD), Duration.ZERO);
        LeakReport report = detector.check(before, policy);
        statistics.record("testA", before, detector.snapshot(), report);

        assertThat(statistics.records()).hasSize(1);
        TestResourceRecord record = statistics.records().get(0);
        assertThat(record.testName()).isEqualTo("testA");
        assertThat(record.deltas())
                .containsEntry(ResourceType.CONNECTION, 1)
                .containsEntry(ResourceType.THREAD, 1)
                .containsEntry(ResourceType.FILE_HANDLE, 0);
        assertThat(record.leakCount()).isEqualTo(1);
        assertThat(record.ignoredGrowthCount()).isEqualTo(1);
        assertThat(record.leaked()).isTrue();
        assertThat(record.recordedAt()).isNotNull();

        assertThat(statistics.totalLeaks()).isEqualTo(1);
        assertThat(statistics.totalIgnored()).isEqualTo(1);
        assertThat(statistics.testsWithLeaks()).isEqualTo(1);
        assertThat(statistics.summary())
                .contains("testA")
                .contains("1 leaked resource(s)")
                .contains("1 ignored allowed growth");
    }

    @Test
    void cleanTestsAccumulateZeroTotals() {
        ResourceRegistry registry = new ResourceRegistry();
        LeakDetector detector = new LeakDetector(new RegistryBackedProvider(registry));
        LeakStatistics statistics = new LeakStatistics();

        ResourceSnapshot before = detector.snapshot();
        try (ResourceLease lease = registry.lease(ResourceType.FILE_HANDLE, "f")) {
            assertThat(lease.resource()).isNotNull();
        }
        LeakReport report = detector.check(before, LeakPolicy.strict());
        statistics.record("cleanTest", before, detector.snapshot(), report);

        assertThat(statistics.totalLeaks()).isZero();
        assertThat(statistics.totalIgnored()).isZero();
        assertThat(statistics.testsWithLeaks()).isZero();
        assertThat(statistics.records().get(0).leaked()).isFalse();
    }
}
