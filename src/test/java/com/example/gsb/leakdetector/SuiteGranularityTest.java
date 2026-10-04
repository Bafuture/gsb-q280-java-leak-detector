package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Covers requirement 5: with {@link Granularity#SUITE} the extension skips
 * per-test checks/statistics and only tracks suite-level attribution.
 */
@DetectLeaks(granularity = Granularity.SUITE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SuiteGranularityTest {

    @Test
    @Order(1)
    void cleanTestUnderSuiteGranularity() {
        try (ResourceLease lease = LeakDetection.registry().lease(ResourceType.CONNECTION, "suite-conn")) {
            assertThat(lease.resource().type()).isEqualTo(ResourceType.CONNECTION);
        }
    }

    @Test
    @Order(2)
    void perTestStatisticsAreSkippedInSuiteOnlyMode() {
        assertThat(LeakDetection.statistics().records())
                .noneMatch(record -> record.testName().contains("cleanTestUnderSuiteGranularity"));
    }
}
