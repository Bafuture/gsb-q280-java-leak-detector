package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeakStatisticsTest {

    @Test
    void aggregatesPerTestDeltasLeakCountAndIgnoredGrowth() {
        SimulatedResources resources = SimulatedResources.withBaseline(Map.of(
                ResourceType.THREAD, 5,
                ResourceType.FILE_HANDLE, 10,
                ResourceType.CONNECTION, 2));
        LeakDetector detector = LeakDetector.builder(resources)
                .allowedGrowth(Set.of(ResourceType.FILE_HANDLE))
                .build();

        ResourceSnapshot firstBaseline = detector.snapshot();
        resources.acquire(ResourceType.THREAD);        // 泄漏 +1
        resources.acquire(ResourceType.FILE_HANDLE, 2); // 允许增长，忽略 +2
        detector.check("testA", firstBaseline);

        ResourceSnapshot secondBaseline = detector.snapshot();
        detector.check("testB", secondBaseline); // 干净测试

        LeakStatistics statistics = detector.statistics();

        assertThat(statistics.leakCount()).isEqualTo(1);
        assertThat(statistics.leakCountFor("testA")).isEqualTo(1);
        assertThat(statistics.leakCountFor("testB")).isZero();
        assertThat(statistics.totalIgnoredGrowth()).isEqualTo(2);
        assertThat(statistics.deltasFor("testA"))
                .containsEntry(ResourceType.THREAD, 1)
                .containsEntry(ResourceType.FILE_HANDLE, 2);
        assertThat(statistics.deltasFor("testB").get(ResourceType.THREAD)).isZero();
        assertThat(statistics.reportsFor("testA")).hasSize(1);
    }

    @Test
    void suiteLevelReportsAreAggregatedPerTest() {
        SimulatedResources resources = new SimulatedResources();
        LeakDetector detector = LeakDetector.builder(resources).build();
        LeakDetectionSession session =
                new LeakDetectionSession(detector, Granularity.PER_SUITE);

        session.beforeAll("suite");
        session.beforeEach("t1");
        session.afterEach("t1");
        session.beforeEach("t2");
        resources.acquire(ResourceType.CONNECTION);
        session.afterEach("t2");
        try {
            session.afterAll("suite");
        } catch (LeakDetectedException expected) {
            // 套件级在 afterAll 抛错，但统计中每个测试的增减已记录
        }

        LeakStatistics statistics = detector.statistics();
        assertThat(statistics.deltasFor("t1").get(ResourceType.CONNECTION)).isZero();
        assertThat(statistics.deltasFor("t2")).containsEntry(ResourceType.CONNECTION, 1);
        assertThat(statistics.leakCount()).isGreaterThanOrEqualTo(1);
    }
}
