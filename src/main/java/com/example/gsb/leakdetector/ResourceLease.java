package com.example.gsb.leakdetector;

import java.util.concurrent.atomic.AtomicBoolean;

/** Auto-closeable handle for a registered resource; closing releases it exactly once. */
public final class ResourceLease implements AutoCloseable {

    private final TrackedResource resource;
    private final ResourceRegistry registry;
    private final AtomicBoolean closed = new AtomicBoolean();

    ResourceLease(TrackedResource resource, ResourceRegistry registry) {
        this.resource = resource;
        this.registry = registry;
    }

    public TrackedResource resource() {
        return resource;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            registry.release(resource);
        }
    }
}
