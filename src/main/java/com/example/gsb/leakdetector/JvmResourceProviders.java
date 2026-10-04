package com.example.gsb.leakdetector;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 基于 JVM 真实指标的默认资源提供者：
 * 活跃线程数取自 {@code ThreadMXBean}，文件句柄数统计 {@code /proc/self/fd}
 * （仅 Linux；其他平台返回 -1，不参与增长判定），连接数来自全局登记处。
 */
public final class JvmResourceProviders {

    private JvmResourceProviders() {
    }

    public static ResourceProvider defaults(ResourceRegistry connectionRegistry) {
        return ResourceProviders.of(Map.of(
                ResourceType.THREAD,
                () -> ManagementFactory.getThreadMXBean().getThreadCount(),
                ResourceType.FILE_HANDLE,
                JvmResourceProviders::openFileHandleCount,
                ResourceType.CONNECTION,
                () -> connectionRegistry.liveCount(ResourceType.CONNECTION)));
    }

    /**
     * 统计当前进程打开的文件描述符数量；平台不支持时返回 -1。
     */
    static int openFileHandleCount() {
        try (Stream<Path> entries = Files.list(Path.of("/proc/self/fd"))) {
            return (int) entries.count();
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            return -1;
        }
    }
}
