package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeakDetectorTest {

    private SimulatedResources resources;
    private ResourceRegistry registry;
    private LeakDetector detector;

    @BeforeEach
    void setUp() {
        resources = SimulatedResources.withBaseline(Map.of(
                ResourceType.THREAD, 5,
                ResourceType.FILE_HANDLE, 10,
                ResourceType.CONNECTION, 2));
        registry = new ResourceRegistry();
        detector = LeakDetector.builder(resources).registry(registry).build();
    }

    @Test
    void detectsLeakWhenResourceCountGrows() {
        ResourceSnapshot before = detector.snapshot();
        resources.acquire(ResourceType.THREAD);

        LeakReport report = detector.check("leakyTest", before);

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leaks()).containsEntry(ResourceType.THREAD, 1);
        assertThat(report.deltas()).containsEntry(ResourceType.CONNECTION, 0);
    }

    @Test
    void passesWhenResourcesAreReleasedBeforeTestEnds() {
        ResourceSnapshot before = detector.snapshot();
        resources.acquire(ResourceType.CONNECTION);
        resources.release(ResourceType.CONNECTION);

        LeakReport report = detector.check("cleanTest", before);

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.leaks()).isEmpty();
    }

    @Test
    void shrinkingCountsAreNotLeaks() {
        ResourceSnapshot before = detector.snapshot();
        resources.release(ResourceType.FILE_HANDLE, 3);

        LeakReport report = detector.check("shrinkingTest", before);

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.deltas()).containsEntry(ResourceType.FILE_HANDLE, -3);
    }

    @Test
    void allowedGrowthIsExcludedFromLeaksAndCountedAsIgnored() {
        LeakDetector tolerant = LeakDetector.builder(resources)
                .allowedGrowth(Set.of(ResourceType.CONNECTION))
                .build();
        ResourceSnapshot before = tolerant.snapshot();
        resources.acquire(ResourceType.CONNECTION, 2);

        LeakReport report = tolerant.check("growingTest", before);

        assertThat(report.hasLeaks()).isFalse();
        assertThat(report.ignoredGrowth()).containsEntry(ResourceType.CONNECTION, 2);
        assertThat(report.ignoredGrowthTotal()).isEqualTo(2);
    }

    @Test
    void gracePeriodToleratesResourcesReleasedAsynchronously() {
        AtomicInteger connections = new AtomicInteger(2);
        ResourceProvider provider = () -> ResourceSnapshot.of(
                Map.of(ResourceType.CONNECTION, connections.get()));
        LeakDetector graceful = LeakDetector.builder(provider)
                .gracePeriod(Duration.ofMillis(500))
                .pollInterval(Duration.ofMillis(10))
                .build();
        ResourceSnapshot before = graceful.snapshot();
        connections.set(3); // 资源正在异步回收中
        Thread reaper = new Thread(() -> {
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            connections.set(2);
        });
        reaper.start();

        LeakReport report = graceful.check("asyncCleanupTest", before);

        assertThat(report.hasLeaks()).isFalse();
    }

    @Test
    void stillReportsLeakWhenGracePeriodExpires() {
        AtomicInteger connections = new AtomicInteger(2);
        ResourceProvider provider = () -> ResourceSnapshot.of(
                Map.of(ResourceType.CONNECTION, connections.get()));
        LeakDetector graceful = LeakDetector.builder(provider)
                .gracePeriod(Duration.ofMillis(100))
                .pollInterval(Duration.ofMillis(10))
                .build();
        ResourceSnapshot before = graceful.snapshot();
        connections.set(4); // 始终未回收

        LeakReport report = graceful.check("stillLeakingTest", before);

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leaks()).containsEntry(ResourceType.CONNECTION, 2);
    }

    @Test
    void reportCarriesCreationSitesOfLeakedRegisteredResources() {
        ResourceSnapshot before = detector.snapshot();
        TrackedResource leaked = registry.register(ResourceType.CONNECTION, "jdbc:orders-db");
        resources.acquire(ResourceType.CONNECTION);

        LeakReport report = detector.check("locatingTest", before);

        assertThat(report.hasLeaks()).isTrue();
        assertThat(report.leakedResources()).containsExactly(leaked);
        assertThat(report.describe())
                .contains("jdbc:orders-db")
                .contains("locatingTest")
                .contains(LeakDetectorTest.class.getSimpleName());
        leaked.release();
    }
}
