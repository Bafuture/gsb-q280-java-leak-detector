package com.example.gsb.leakdetector;

import java.util.Objects;

/**
 * 框架与运行环境之间的接线点：持有当前使用的 {@link ResourceProvider}
 * 与全局连接登记处。测试可用 {@link #useProvider} 替换为模拟提供者，
 * 生产代码通过 {@link #registry()} 登记连接以获得调用点定位能力。
 */
public final class LeakDetectionEnvironment {

    private static final ResourceRegistry REGISTRY = new ResourceRegistry();
    private static volatile ResourceProvider provider = JvmResourceProviders.defaults(REGISTRY);

    private LeakDetectionEnvironment() {
    }

    public static ResourceProvider provider() {
        return provider;
    }

    public static void useProvider(ResourceProvider newProvider) {
        provider = Objects.requireNonNull(newProvider, "newProvider");
    }

    /**
     * 恢复为基于 JVM 真实指标的默认提供者。
     */
    public static void reset() {
        provider = JvmResourceProviders.defaults(REGISTRY);
    }

    /**
     * 全局资源登记处：业务代码创建连接等资源时在此登记，泄漏报告即可定位创建点。
     */
    public static ResourceRegistry registry() {
        return REGISTRY;
    }
}
