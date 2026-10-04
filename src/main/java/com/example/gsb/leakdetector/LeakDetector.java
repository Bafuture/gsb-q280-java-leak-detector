package com.example.gsb.leakdetector;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * 泄漏判定器：对比测试前后的资源快照，结束后数量多于开始即视为泄漏；
 * 支持“允许增长”白名单与“延迟回收容忍”（宽限期内轮询重采，避免误报
 * 正在异步回收的资源）。
 */
public final class LeakDetector {

    private final ResourceProvider provider;
    private final Set<ResourceType> allowedGrowth;
    private final Duration gracePeriod;
    private final Duration pollInterval;
    private final ResourceRegistry registry;
    private final LeakStatistics statistics = new LeakStatistics();

    private LeakDetector(Builder builder) {
        this.provider = builder.provider;
        this.allowedGrowth = builder.allowedGrowth;
        this.gracePeriod = builder.gracePeriod;
        this.pollInterval = builder.pollInterval;
        this.registry = builder.registry;
    }

    public static Builder builder(ResourceProvider provider) {
        return new Builder(provider);
    }

    /**
     * 采集当前资源快照。
     */
    public ResourceSnapshot snapshot() {
        return provider.snapshot();
    }

    public LeakStatistics statistics() {
        return statistics;
    }

    /**
     * 仅对比两份快照，不触发宽限等待、不记入统计。
     */
    public LeakReport compare(String testName, ResourceSnapshot before, ResourceSnapshot after) {
        return LeakReport.analyze(testName, before, after, allowedGrowth, registry);
    }

    /**
     * 完整检测：采集结束快照，若发现泄漏且配置了宽限期，则在宽限期内轮询等待
     * 资源回收后再判定；结果记入统计。
     */
    public LeakReport check(String testName, ResourceSnapshot before) {
        Objects.requireNonNull(before, "before");
        ResourceSnapshot after = provider.snapshot();
        if (hasLeak(before, after) && !gracePeriod.isZero() && !gracePeriod.isNegative()) {
            after = awaitRecovery(before);
        }
        LeakReport report = compare(testName, before, after);
        statistics.record(report);
        return report;
    }

    boolean hasLeak(ResourceSnapshot before, ResourceSnapshot after) {
        for (ResourceType type : ResourceType.values()) {
            if (!allowedGrowth.contains(type) && after.count(type) > before.count(type)) {
                return true;
            }
        }
        return false;
    }

    private ResourceSnapshot awaitRecovery(ResourceSnapshot before) {
        long deadlineNanos = System.nanoTime() + gracePeriod.toNanos();
        ResourceSnapshot latest = provider.snapshot();
        while (hasLeak(before, latest)) {
            long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000;
            if (remainingMillis <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(pollInterval.toMillis(), remainingMillis));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            latest = provider.snapshot();
        }
        return latest;
    }

    public static final class Builder {

        private final ResourceProvider provider;
        private Set<ResourceType> allowedGrowth = Set.of();
        private Duration gracePeriod = Duration.ZERO;
        private Duration pollInterval = Duration.ofMillis(20);
        private ResourceRegistry registry;

        private Builder(ResourceProvider provider) {
            this.provider = Objects.requireNonNull(provider, "provider");
        }

        /**
         * 允许增长的资源类型：这些类型数量增加不视为泄漏（计入“被忽略的增长”）。
         */
        public Builder allowedGrowth(Set<ResourceType> types) {
            this.allowedGrowth = Set.copyOf(types);
            return this;
        }

        /**
         * 延迟回收宽限期：初次判定泄漏后等待该时长并轮询重采，再最终判定。
         */
        public Builder gracePeriod(Duration gracePeriod) {
            this.gracePeriod = Objects.requireNonNull(gracePeriod);
            return this;
        }

        /**
         * 宽限期内的重采间隔，默认 20ms。
         */
        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = Objects.requireNonNull(pollInterval);
            return this;
        }

        /**
         * 关联资源登记处，使泄漏报告能携带创建调用点。
         */
        public Builder registry(ResourceRegistry registry) {
            this.registry = registry;
            return this;
        }

        public LeakDetector build() {
            return new LeakDetector(this);
        }
    }
}
