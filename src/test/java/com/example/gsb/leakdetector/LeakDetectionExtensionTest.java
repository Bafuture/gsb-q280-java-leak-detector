package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Covers requirement 5 (per-test granularity via the JUnit extension) and
 * requirement 6 (statistics recorded by the extension). Ordered so the
 * statistics assertion runs after the resource-using test.
 */
@DetectLeaks(toleranceMillis = 200)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LeakDetectionExtensionTest {

    @Test
    @Order(1)
    void releasedResourcePassesThePerTestCheck() {
        try (ResourceLease lease = LeakDetection.registry().lease(ResourceType.FILE_HANDLE, "temp-file")) {
            assertThat(LeakDetection.registry().counts().get(ResourceType.FILE_HANDLE))
                    .isGreaterThanOrEqualTo(1);
            assertThat(lease.resource().callSite().className())
                    .isEqualTo(LeakDetectionExtensionTest.class.getName());
        }
    }

    @Test
    @Order(2)
    void perTestStatisticsAreRecordedByTheExtension() {
        assertThat(LeakDetection.statistics().records())
                .anyMatch(record -> record.testName().contains("releasedResourcePassesThePerTestCheck")
                        && !record.leaked());
    }
}
