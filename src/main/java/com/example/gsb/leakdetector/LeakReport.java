package com.example.gsb.leakdetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一次泄漏判定的结果：前后快照、各资源增减、判定为泄漏的增长、
 * 被“允许增长”规则忽略的增长，以及（若提供登记处）泄漏资源的创建调用点。
 */
public final class LeakReport {

    private final String testName;
    private final ResourceSnapshot before;
    private final ResourceSnapshot after;
    private final EnumMap<ResourceType, Integer> deltas;
    private final EnumMap<ResourceType, Integer> leaks;
    private final EnumMap<ResourceType, Integer> ignoredGrowth;
    private final List<TrackedResource> leakedResources;

    private LeakReport(String testName, ResourceSnapshot before, ResourceSnapshot after,
                       EnumMap<ResourceType, Integer> deltas,
                       EnumMap<ResourceType, Integer> leaks,
                       EnumMap<ResourceType, Integer> ignoredGrowth,
                       List<TrackedResource> leakedResources) {
        this.testName = testName;
        this.before = before;
        this.after = after;
        this.deltas = deltas;
        this.leaks = leaks;
        this.ignoredGrowth = ignoredGrowth;
        this.leakedResources = leakedResources;
    }

    static LeakReport analyze(String testName, ResourceSnapshot before, ResourceSnapshot after,
                              Set<ResourceType> allowedGrowth, ResourceRegistry registry) {
        EnumMap<ResourceType, Integer> deltas = after.delta(before);
        EnumMap<ResourceType, Integer> leaks = new EnumMap<>(ResourceType.class);
        EnumMap<ResourceType, Integer> ignored = new EnumMap<>(ResourceType.class);
        List<TrackedResource> leakedResources = new ArrayList<>();
        for (Map.Entry<ResourceType, Integer> entry : deltas.entrySet()) {
            int delta = entry.getValue();
            if (delta <= 0) {
                continue;
            }
            if (allowedGrowth.contains(entry.getKey())) {
                ignored.put(entry.getKey(), delta);
            } else {
                leaks.put(entry.getKey(), delta);
                if (registry != null) {
                    leakedResources.addAll(registry.liveResources(entry.getKey()));
                }
            }
        }
        return new LeakReport(testName, before, after, deltas, leaks, ignored,
                List.copyOf(leakedResources));
    }

    public String testName() {
        return testName;
    }

    public ResourceSnapshot before() {
        return before;
    }

    public ResourceSnapshot after() {
        return after;
    }

    /**
     * 各资源类型相对基线的增减量（含零与负值）。
     */
    public Map<ResourceType, Integer> deltas() {
        return Collections.unmodifiableMap(deltas);
    }

    /**
     * 判定为泄漏的增长（正增长且不属于允许增长的类型）。
     */
    public Map<ResourceType, Integer> leaks() {
        return Collections.unmodifiableMap(leaks);
    }

    /**
     * 被“允许增长”规则忽略的正增长。
     */
    public Map<ResourceType, Integer> ignoredGrowth() {
        return Collections.unmodifiableMap(ignoredGrowth);
    }

    /**
     * 泄漏类型下仍未释放的登记资源（含创建调用点）；未配置登记处时为空。
     */
    public List<TrackedResource> leakedResources() {
        return leakedResources;
    }

    public boolean hasLeaks() {
        return !leaks.isEmpty();
    }

    public int ignoredGrowthTotal() {
        return ignoredGrowth.values().stream().mapToInt(Integer::intValue).sum();
    }

    public String describe() {
        if (!hasLeaks()) {
            return "no resource leak detected in '" + testName + "'";
        }
        StringBuilder sb = new StringBuilder("resource leak detected in '").append(testName).append("':");
        leaks.forEach((type, delta) -> sb.append("\n  ").append(type)
                .append(" +").append(delta)
                .append(" (").append(before.count(type)).append(" -> ").append(after.count(type)).append(")"));
        for (TrackedResource resource : leakedResources) {
            sb.append("\n  leaked ").append(resource.type())
                    .append(" '").append(resource.description()).append("'")
                    .append(" created at ").append(resource.creationSite());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return describe();
    }
}
