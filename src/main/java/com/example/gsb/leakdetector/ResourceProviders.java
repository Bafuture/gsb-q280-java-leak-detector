package com.example.gsb.leakdetector;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * 从一组“按资源类型取值”的计数器组装 {@link ResourceProvider}。
 */
public final class ResourceProviders {

    private ResourceProviders() {
    }

    /**
     * 示例：
     * <pre>{@code
     * ResourceProviders.of(Map.of(
     *         ResourceType.THREAD, () -> ManagementFactory.getThreadMXBean().getThreadCount(),
     *         ResourceType.FILE_HANDLE, JvmResourceProviders::openFileHandleCount));
     * }</pre>
     */
    public static ResourceProvider of(Map<ResourceType, ? extends IntSupplier> suppliers) {
        return () -> {
            EnumMap<ResourceType, Integer> counts = new EnumMap<>(ResourceType.class);
            suppliers.forEach((type, supplier) -> counts.put(type, supplier.getAsInt()));
            return ResourceSnapshot.of(counts);
        };
    }
}
