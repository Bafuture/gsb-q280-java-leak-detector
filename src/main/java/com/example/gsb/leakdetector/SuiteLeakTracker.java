package com.example.gsb.leakdetector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Suite-granularity tracker. A baseline is taken when the suite starts and a
 * checkpoint is taken after every test. Newly observed resources are
 * attributed to the test in which they first appeared, so a suite-level leak
 * can be traced to the specific test that introduced it even when the leak
 * stays present through later tests.
 */
public final class SuiteLeakTracker {

    private final LeakDetector detector;
    private final LeakPolicy policy;
    private final Map<String, List<TrackedResource>> leaksByTest = new LinkedHashMap<>();
    private final Map<String, Integer> ignoredByTest = new LinkedHashMap<>();

    private ResourceSnapshot checkpoint;
    private Set<TrackedResource> suiteBaselineResources;

    public SuiteLeakTracker(LeakDetector detector, LeakPolicy policy) {
        this.detector = detector;
        this.policy = policy;
    }

    public void startSuite() {
        this.checkpoint = detector.snapshot();
        this.suiteBaselineResources = checkpoint.resources();
        this.leaksByTest.clear();
        this.ignoredByTest.clear();
    }

    /** Called after each test; attributes resources newly seen since the last checkpoint. */
    public LeakReport afterTest(String testName) {
        LeakReport report = detector.check(checkpoint, policy);
        if (report.hasLeaks()) {
            leaksByTest.computeIfAbsent(testName, ignored -> new ArrayList<>())
                    .addAll(report.leakedResources().values().stream()
                            .flatMap(List::stream)
                            .toList());
        }
        if (report.totalIgnoredCount() > 0) {
            ignoredByTest.merge(testName, report.totalIgnoredCount(), Integer::sum);
        }
        checkpoint = detector.snapshot();
        return report;
    }

    /** Called when the suite finishes; returns the attributions gathered so far. */
    public SuiteLeakReport finishSuite() {
        ResourceSnapshot finalSnapshot = detector.snapshot();
        Map<String, List<TrackedResource>> stillLeakingByTest = new LinkedHashMap<>();
        if (suiteBaselineResources != null) {
            for (Map.Entry<String, List<TrackedResource>> entry : leaksByTest.entrySet()) {
                List<TrackedResource> stillLeaking = entry.getValue().stream()
                        .filter(r -> finalSnapshot.resources().contains(r))
                        .toList();
                if (!stillLeaking.isEmpty()) {
                    stillLeakingByTest.put(entry.getKey(), stillLeaking);
                }
            }
        }
        return new SuiteLeakReport(stillLeakingByTest, ignoredByTest);
    }
}
