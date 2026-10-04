package com.example.gsb.leakdetector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源登记处：业务代码在创建资源（如打开连接）时调用 {@link #register}，
 * 框架记录创建调用点；资源关闭时调用 {@link TrackedResource#release()} 注销。
 * 泄漏报告中未注销的记录即泄漏嫌疑对象，可直接定位到创建位置。
 */
public final class ResourceRegistry {

    private final Map<UUID, TrackedResource> live = new ConcurrentHashMap<>();

    /**
     * 登记一个新建资源，自动捕获调用点。
     */
    public TrackedResource register(ResourceType type, String description) {
        TrackedResource resource = new TrackedResource(this, type, description, captureCreationSite());
        live.put(resource.id(), resource);
        return resource;
    }

    void release(TrackedResource resource) {
        live.remove(resource.id());
    }

    public int liveCount(ResourceType type) {
        int count = 0;
        for (TrackedResource resource : live.values()) {
            if (resource.type() == type) {
                count++;
            }
        }
        return count;
    }

    /**
     * 当前仍未释放的某类资源（含创建调用点）。
     */
    public List<TrackedResource> liveResources(ResourceType type) {
        return live.values().stream()
                .filter(resource -> resource.type() == type)
                .toList();
    }

    private static StackTraceElement captureCreationSite() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (!className.equals(ResourceRegistry.class.getName())
                    && !className.equals(TrackedResource.class.getName())
                    && !className.startsWith("java.lang.")) {
                return element;
            }
        }
        return stack[stack.length - 1];
    }
}
