package com.example.gsb.leakdetector;

import java.util.Map;

/**
 * 检测到资源泄漏时抛出的断言错误，消息中包含泄漏类型、数量变化、
 * 创建调用点以及（套件级粒度下）引入泄漏的测试名。
 */
public class LeakDetectedException extends AssertionError {

    private final LeakReport report;
    private final Map<ResourceType, String> leakIntroducedBy;

    public LeakDetectedException(LeakReport report) {
        this(report, Map.of());
    }

    public LeakDetectedException(LeakReport report, Map<ResourceType, String> leakIntroducedBy) {
        super(buildMessage(report, leakIntroducedBy));
        this.report = report;
        this.leakIntroducedBy = Map.copyOf(leakIntroducedBy);
    }

    public LeakReport report() {
        return report;
    }

    /**
     * 套件级粒度下，各泄漏资源类型首次出现增长的测试名。
     */
    public Map<ResourceType, String> leakIntroducedBy() {
        return leakIntroducedBy;
    }

    private static String buildMessage(LeakReport report, Map<ResourceType, String> introducedBy) {
        StringBuilder sb = new StringBuilder(report.describe());
        introducedBy.forEach((type, testName) -> sb.append("\n  ").append(type)
                .append(" leak introduced by test '").append(testName).append("'"));
        return sb.toString();
    }
}
