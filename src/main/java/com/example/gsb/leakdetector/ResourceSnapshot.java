package com.example.gsb.leakdetector;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 某个时间点上各类资源的数量快照，不可变。
 */
public final class ResourceSnapshot {

    private final EnumMap<ResourceType, Integer> counts;

    private ResourceSnapshot(EnumMap<ResourceType, Integer> counts) {
        this.counts = counts;
    }

    /**
     * 根据计数映射构造快照；未显式给出的资源类型按 0 处理。
     */
    public static ResourceSnapshot of(Map<ResourceType, Integer> counts) {
        Objects.requireNonNull(counts, "counts");
        EnumMap<ResourceType, Integer> copy = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            copy.put(type, counts.getOrDefault(type, 0));
        }
        return new ResourceSnapshot(copy);
    }

    public int count(ResourceType type) {
        return counts.get(type);
    }

    public Map<ResourceType, Integer> counts() {
        return Collections.unmodifiableMap(counts);
    }

    /**
     * 计算相对于基线快照的增减量（this - baseline）：正数表示增长，负数表示减少。
     */
    public EnumMap<ResourceType, Integer> delta(ResourceSnapshot baseline) {
        Objects.requireNonNull(baseline, "baseline");
        EnumMap<ResourceType, Integer> delta = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            delta.put(type, count(type) - baseline.count(type));
        }
        return delta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceSnapshot other)) {
            return false;
        }
        return counts.equals(other.counts);
    }

    @Override
    public int hashCode() {
        return counts.hashCode();
    }

    @Override
    public String toString() {
        return "ResourceSnapshot" + counts;
    }
}
