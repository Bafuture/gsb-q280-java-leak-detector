package com.example.gsb.leakdetector;

import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * JUnit 5 扩展：按 {@link LeakDetection} 注解配置，在测试前后自动采集
 * 资源快照并执行泄漏判定。通常无需直接引用，使用 {@link LeakDetection} 注解即可。
 */
public final class LeakDetectionExtension
        implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback, AfterAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(LeakDetectionExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        session(context).beforeAll(context.getDisplayName());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        session(context).beforeEach(context.getDisplayName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        session(context).afterEach(context.getDisplayName());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        session(context).afterAll(context.getDisplayName());
    }

    private LeakDetectionSession session(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        return context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(testClass, key -> createSession(testClass), LeakDetectionSession.class);
    }

    private static LeakDetectionSession createSession(Class<?> testClass) {
        LeakDetection config = testClass.getAnnotation(LeakDetection.class);
        Granularity granularity = config != null ? config.granularity() : Granularity.PER_TEST;
        Set<ResourceType> allowedGrowth = config == null || config.allowedGrowth().length == 0
                ? Set.of()
                : EnumSet.copyOf(Arrays.asList(config.allowedGrowth()));
        Duration grace = config != null ? Duration.ofMillis(config.graceMillis()) : Duration.ZERO;
        Duration poll = config != null ? Duration.ofMillis(config.pollMillis()) : Duration.ofMillis(20);
        LeakDetector detector = LeakDetector.builder(LeakDetectionEnvironment.provider())
                .allowedGrowth(allowedGrowth)
                .gracePeriod(grace)
                .pollInterval(poll)
                .registry(LeakDetectionEnvironment.registry())
                .build();
        return new LeakDetectionSession(detector, granularity);
    }
}
