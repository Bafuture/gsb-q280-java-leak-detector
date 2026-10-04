package com.example.gsb.leakdetector;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 泄漏统计：累计各次检测的报告，提供按测试的资源增减、泄漏次数与
 * 被忽略的允许增长数等聚合视图。
 */
public final class LeakStatistics {

    private final List<LeakReport> reports = new CopyOnWriteArrayList<>();

    public void record(LeakReport report) {
        reports.add(report);
    }

    public List<LeakReport> reports() {
        return List.copyOf(reports);
    }

    public List<LeakReport> reportsFor(String testName) {
        return reports.stream()
                .filter(report -> report.testName().equals(testName))
                .toList();
    }

    /**
     * 判定为泄漏的检测次数。
     */
    public long leakCount() {
        return reports.stream().filter(LeakReport::hasLeaks).count();
    }

    public long leakCountFor(String testName) {
        return reportsFor(testName).stream().filter(LeakReport::hasLeaks).count();
    }

    /**
     * 被“允许增长”规则忽略的资源增长总数。
     */
    public int totalIgnoredGrowth() {
        return reports.stream().mapToInt(LeakReport::ignoredGrowthTotal).sum();
    }

    /**
     * 指定测试在各次检测中的累计资源增减（按资源类型汇总）。
     */
    public Map<ResourceType, Integer> deltasFor(String testName) {
        EnumMap<ResourceType, Integer> totals = new EnumMap<>(ResourceType.class);
        for (LeakReport report : reportsFor(testName)) {
            report.deltas().forEach((type, delta) -> totals.merge(type, delta, Integer::sum));
        }
        return totals;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder("leak statistics: ")
                .append(reports.size()).append(" check(s), ")
                .append(leakCount()).append(" leak(s), ")
                .append(totalIgnoredGrowth()).append(" ignored allowed growth");
        for (LeakReport report : reports) {
            sb.append("\n  ").append(report.testName())
                    .append(" deltas=").append(report.deltas())
                    .append(report.hasLeaks() ? " LEAK" : " ok");
        }
        return sb.toString();
    }
}
