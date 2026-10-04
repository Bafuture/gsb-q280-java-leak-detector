package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeakDetectionSessionTest {

    private SimulatedResources resources;
    private LeakDetector detector;

    @BeforeEach
    void setUp() {
        resources = new SimulatedResources();
        detector = LeakDetector.builder(resources).build();
    }

    @Test
    void perTestGranularityFailsOnlyTheLeakingTest() {
        LeakDetectionSession session =
                new LeakDetectionSession(detector, Granularity.PER_TEST);

        session.beforeEach("cleanTest");
        assertThatCode(() -> session.afterEach("cleanTest")).doesNotThrowAnyException();

        session.beforeEach("leakyTest");
        resources.acquire(ResourceType.THREAD);
        assertThatThrownBy(() -> session.afterEach("leakyTest"))
                .isInstanceOf(LeakDetectedException.class)
                .hasMessageContaining("leakyTest")
                .hasMessageContaining("THREAD");
    }

    @Test
    void suiteGranularityAttributesLeakToTheIntroducingTest() {
        LeakDetectionSession session =
                new LeakDetectionSession(detector, Granularity.PER_SUITE);
        session.beforeAll("mySuite");

        session.beforeEach("firstTest");
        assertThatCode(() -> session.afterEach("firstTest")).doesNotThrowAnyException();

        resources.acquire(ResourceType.CONNECTION); // 泄漏由第二个测试引入
        session.beforeEach("secondTest");
        // 套件级：单个测试结束时不立即失败，便于全部测试跑完后统一报告
        assertThatCode(() -> session.afterEach("secondTest")).doesNotThrowAnyException();

        assertThatThrownBy(() -> session.afterAll("mySuite"))
                .isInstanceOf(LeakDetectedException.class)
                .hasMessageContaining("secondTest")
                .hasMessageContaining("CONNECTION");
    }

    @Test
    void suiteGranularityPassesWhenEveryTestCleansUp() {
        LeakDetectionSession session =
                new LeakDetectionSession(detector, Granularity.PER_SUITE);
        session.beforeAll("cleanSuite");

        session.beforeEach("a");
        resources.acquire(ResourceType.THREAD);
        resources.release(ResourceType.THREAD);
        session.afterEach("a");

        session.beforeEach("b");
        session.afterEach("b");

        assertThatCode(() -> session.afterAll("cleanSuite")).doesNotThrowAnyException();
    }

    @Test
    void perTestBaselinesAreIndependentAcrossTests() {
        LeakDetectionSession session =
                new LeakDetectionSession(detector, Granularity.PER_TEST);

        session.beforeEach("first");
        resources.acquire(ResourceType.FILE_HANDLE, 2);
        resources.release(ResourceType.FILE_HANDLE, 2);
        session.afterEach("first");

        session.beforeEach("second");
        assertThatCode(() -> session.afterEach("second")).doesNotThrowAnyException();
    }
}
