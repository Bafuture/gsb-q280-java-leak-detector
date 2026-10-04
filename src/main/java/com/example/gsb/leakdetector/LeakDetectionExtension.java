package com.example.gsb.leakdetector;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * JUnit 5 extension backing {@link DetectLeaks}. Depending on the configured
 * {@link Granularity} it checks for leaks after every test and/or after the
 * whole suite (attributing suite leaks to the introducing test), and records
 * per-test statistics into {@link LeakDetection#statistics()}.
 */
public class LeakDetectionExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Namespace BASELINE_NAMESPACE = Namespace.create(LeakDetectionExtension.class, "baseline");
    private static final String BASELINE_KEY = "baseline";

    private static final Map<Class<?>, SuiteLeakTracker> SUITE_TRACKERS = new ConcurrentHashMap<>();

    @Override
    public void beforeAll(ExtensionContext context) {
        Config config = Config.from(context);
        if (config.granularity().includesSuite()) {
            SuiteLeakTracker tracker = new SuiteLeakTracker(LeakDetection.detector(), config.policy());
            tracker.startSuite();
            SUITE_TRACKERS.put(context.getRequiredTestClass(), tracker);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Config config = Config.from(context);
        if (config.granularity().includesTest()) {
            context.getStore(BASELINE_NAMESPACE).put(BASELINE_KEY, LeakDetection.detector().snapshot());
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Config config = Config.from(context);
        String testName = context.getDisplayName();
        if (config.granularity().includesTest()) {
            ResourceSnapshot baseline = context.getStore(BASELINE_NAMESPACE).remove(BASELINE_KEY, ResourceSnapshot.class);
            if (baseline != null) {
                LeakDetector detector = LeakDetection.detector();
                LeakReport report = detector.check(baseline, config.policy());
                LeakDetection.statistics().record(testName, baseline, detector.snapshot(), report);
                report.throwIfLeaked("test '" + testName + "'");
            }
        }
        SuiteLeakTracker tracker = SUITE_TRACKERS.get(context.getRequiredTestClass());
        if (tracker != null) {
            tracker.afterTest(testName);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        SuiteLeakTracker tracker = SUITE_TRACKERS.remove(context.getRequiredTestClass());
        if (tracker != null) {
            SuiteLeakReport report = tracker.finishSuite();
            if (report.hasLeaks()) {
                throw new LeakDetectionException("Resource leak detected in suite '"
                        + context.getDisplayName() + "'" + System.lineSeparator() + report.describe());
            }
        }
    }

    private record Config(LeakPolicy policy, Granularity granularity) {

        static Config from(ExtensionContext context) {
            DetectLeaks annotation = context.getRequiredTestClass().getAnnotation(DetectLeaks.class);
            if (annotation == null) {
                return new Config(LeakPolicy.strict(), Granularity.TEST_AND_SUITE);
            }
            LeakPolicy policy = new LeakPolicy(
                    annotation.allowedGrowth().length == 0
                            ? java.util.Set.of()
                            : java.util.Set.of(annotation.allowedGrowth()),
                    Duration.ofMillis(Math.max(0, annotation.toleranceMillis())));
            return new Config(policy, annotation.granularity());
        }
    }
}
