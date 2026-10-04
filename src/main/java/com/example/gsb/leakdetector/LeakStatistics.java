package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects per-test resource statistics across a run. Thread-safe. */
public final class LeakStatistics {

    private final List<TestResourceRecord> records = new CopyOnWriteArrayList<>();

    /** Records one test's outcome from its before/after snapshots and leak report. */
    public TestResourceRecord record(String testName, ResourceSnapshot before, ResourceSnapshot after, LeakReport report) {
        Map<ResourceType, Integer> deltas = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            deltas.put(type, after.delta(type, before));
        }
        TestResourceRecord record = new TestResourceRecord(
                testName, deltas, report.totalLeakCount(), report.totalIgnoredCount(), Instant.now());
        records.add(record);
        return record;
    }

    public List<TestResourceRecord> records() {
        return List.copyOf(records);
    }

    public int totalLeaks() {
        return records.stream().mapToInt(TestResourceRecord::leakCount).sum();
    }

    public int totalIgnored() {
        return records.stream().mapToInt(TestResourceRecord::ignoredGrowthCount).sum();
    }

    public long testsWithLeaks() {
        return records.stream().filter(TestResourceRecord::leaked).count();
    }

    public String summary() {
        StringBuilder builder = new StringBuilder("Leak statistics: ")
                .append(records.size()).append(" test(s), ")
                .append(totalLeaks()).append(" leaked resource(s), ")
                .append(totalIgnored()).append(" ignored allowed growth");
        records.forEach(record -> builder.append(System.lineSeparator())
                .append("  ").append(record.testName())
                .append(" deltas=").append(record.deltas())
                .append(" leaks=").append(record.leakCount())
                .append(" ignored=").append(record.ignoredGrowthCount()));
        return builder.toString();
    }

    public void clear() {
        records.clear();
    }
}
