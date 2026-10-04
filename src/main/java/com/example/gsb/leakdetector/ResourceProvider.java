package com.example.gsb.leakdetector;

/**
 * 资源数据提供者：返回当前时刻的资源数量快照。
 *
 * <p>生产环境可使用真实 JVM 指标（见 {@link JvmResourceProviders}），测试中可替换为
 * 可随意增减计数的模拟实现，使泄漏判定逻辑完全可测、无环境噪声。
 */
@FunctionalInterface
public interface ResourceProvider {

    ResourceSnapshot snapshot();
}
