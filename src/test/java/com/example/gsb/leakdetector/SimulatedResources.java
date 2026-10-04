package com.example.gsb.leakdetector;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可随意增减计数的模拟资源提供者，用于在无真实线程/句柄/连接的环境中
 * 确定性地验证泄漏判定逻辑。
 */
final class SimulatedResources implements ResourceProvider {

    private final EnumMap<ResourceType, AtomicInteger> counts = new EnumMap<>(ResourceType.class);

    SimulatedResources() {
        for (ResourceType type : ResourceType.values()) {
            counts.put(type, new AtomicInteger());
        }
    }

    void acquire(ResourceType type) {
        acquire(type, 1);
    }

    void acquire(ResourceType type, int n) {
        counts.get(type).addAndGet(n);
    }

    void release(ResourceType type) {
        release(type, 1);
    }

    void release(ResourceType type, int n) {
        counts.get(type).addAndGet(-n);
    }

    void set(ResourceType type, int value) {
        counts.get(type).set(value);
    }

    @Override
    public ResourceSnapshot snapshot() {
        EnumMap<ResourceType, Integer> snapshot = new EnumMap<>(ResourceType.class);
        counts.forEach((type, counter) -> snapshot.put(type, counter.get()));
        return ResourceSnapshot.of(snapshot);
    }

    static SimulatedResources withBaseline(Map<ResourceType, Integer> baseline) {
        SimulatedResources resources = new SimulatedResources();
        baseline.forEach(resources::set);
        return resources;
    }
}
