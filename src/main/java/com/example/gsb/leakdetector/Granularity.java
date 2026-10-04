package com.example.gsb.leakdetector;

/**
 * 泄漏检测粒度。
 */
public enum Granularity {
    /**
     * 单测级：每个测试方法前后各采一次快照，泄漏立即使该测试失败。
     */
    PER_TEST,
    /**
     * 套件级：整个测试类开始前采基线，全部测试结束后统一判定；
     * 通过每个测试后的中间快照定位引入泄漏的具体测试。
     */
    PER_SUITE
}
