package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * 通过 JUnit 5 扩展真实执行的端到端测试：类加载（静态初始化）时把全局
 * 提供者替换为模拟提供者，所有测试都正确释放资源，因此全部通过；
 * FILE_HANDLE 通过 allowedGrowth 允许增长。
 */
@LeakDetection(
        granularity = Granularity.PER_TEST,
        allowedGrowth = ResourceType.FILE_HANDLE,
        graceMillis = 50)
class LeakDetectionExtensionIntegrationTest {

    private static final SimulatedResources RESOURCES;

    static {
        SimulatedResources simulated = SimulatedResources.withBaseline(java.util.Map.of(
                ResourceType.THREAD, 4,
                ResourceType.FILE_HANDLE, 8,
                ResourceType.CONNECTION, 1));
        LeakDetectionEnvironment.useProvider(simulated);
        RESOURCES = simulated;
    }

    @AfterAll
    static void restoreDefaultProvider() {
        LeakDetectionEnvironment.reset();
    }

    @Test
    void threadAcquiredAndReleased() {
        RESOURCES.acquire(ResourceType.THREAD);
        RESOURCES.release(ResourceType.THREAD);
    }

    @Test
    void registeredConnectionIsClosed() {
        RESOURCES.acquire(ResourceType.CONNECTION);
        try (TrackedResource ignored =
                     LeakDetectionEnvironment.registry().register(ResourceType.CONNECTION, "it-conn")) {
            assertThat(ignored.creationSite().getMethodName())
                    .isEqualTo("registeredConnectionIsClosed");
        } finally {
            RESOURCES.release(ResourceType.CONNECTION);
        }
    }

    @Test
    void allowedFileHandleGrowthDoesNotFailTheTest() {
        RESOURCES.acquire(ResourceType.FILE_HANDLE, 3);
    }

    @Test
    void noResourceUsageAtAll() {
    }
}
