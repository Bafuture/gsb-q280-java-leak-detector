package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一条已登记的资源记录，包含创建时的调用点（类、方法、行号），
 * 用于泄漏发生后回溯到资源的创建位置。
 */
public final class TrackedResource implements AutoCloseable {

    private final UUID id = UUID.randomUUID();
    private final ResourceRegistry registry;
    private final ResourceType type;
    private final String description;
    private final Instant createdAt = Instant.now();
    private final StackTraceElement creationSite;
    private final AtomicBoolean released = new AtomicBoolean();

    TrackedResource(ResourceRegistry registry, ResourceType type, String description,
                    StackTraceElement creationSite) {
        this.registry = registry;
        this.type = type;
        this.description = description;
        this.creationSite = creationSite;
    }

    public UUID id() {
        return id;
    }

    public ResourceType type() {
        return type;
    }

    public String description() {
        return description;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /**
     * 资源创建处的调用点（跳过框架自身栈帧后的第一帧业务代码）。
     */
    public StackTraceElement creationSite() {
        return creationSite;
    }

    public boolean isReleased() {
        return released.get();
    }

    /**
     * 释放登记；幂等，可重复调用。
     */
    public void release() {
        if (released.compareAndSet(false, true)) {
            registry.release(this);
        }
    }

    @Override
    public void close() {
        release();
    }

    @Override
    public String toString() {
        return type + " '" + description + "' created at " + creationSite;
    }
}
