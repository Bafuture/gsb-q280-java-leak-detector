package com.example.gsb.leakdetector;

import java.util.EnumMap;

/**
 * 一次测试运行的检测会话，封装两种粒度的判定流程，供
 * {@link LeakDetectionExtension} 调用，也可在单元测试中直接驱动。
 */
public final class LeakDetectionSession {

    private final LeakDetector detector;
    private final Granularity granularity;
    private final EnumMap<ResourceType, String> leakIntroducedBy = new EnumMap<>(ResourceType.class);
    private ResourceSnapshot suiteBaseline;
    private ResourceSnapshot testBaseline;

    public LeakDetectionSession(LeakDetector detector, Granularity granularity) {
        this.detector = detector;
        this.granularity = granularity;
    }

    public LeakDetector detector() {
        return detector;
    }

    public void beforeAll(String suiteName) {
        if (granularity == Granularity.PER_SUITE) {
            suiteBaseline = detector.snapshot();
        }
    }

    public void beforeEach(String testName) {
        if (granularity == Granularity.PER_TEST) {
            testBaseline = detector.snapshot();
        }
    }

    public void afterEach(String testName) {
        if (granularity == Granularity.PER_TEST) {
            LeakReport report = detector.check(testName, testBaseline);
            if (report.hasLeaks()) {
                throw new LeakDetectedException(report);
            }
        } else {
            // 套件级：每个测试后与套件基线对比，记录首次出现增长的测试，但不中断执行。
            LeakReport report = detector.compare(testName, suiteBaseline, detector.snapshot());
            report.leaks().keySet().forEach(type -> leakIntroducedBy.putIfAbsent(type, testName));
            detector.statistics().record(report);
        }
    }

    public void afterAll(String suiteName) {
        if (granularity != Granularity.PER_SUITE) {
            return;
        }
        LeakReport report = detector.check(suiteName, suiteBaseline);
        if (report.hasLeaks()) {
            throw new LeakDetectedException(report, leakIntroducedBy);
        }
    }
}
