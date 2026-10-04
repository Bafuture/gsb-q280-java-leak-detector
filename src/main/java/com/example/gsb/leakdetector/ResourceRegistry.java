package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Thread-safe registry of live resources. Production code (or test doubles)
 * registers a resource on creation and releases it on disposal. Each
 * registration captures the {@link CallSite} so leaks can be located.
 */
public final class ResourceRegistry {

    private final AtomicLong ids = new AtomicLong();
    private final Map<Long, TrackedResource> active = new ConcurrentHashMap<>();

    /** Registers a resource and returns its handle. */
    public TrackedResource register(ResourceType type, String description) {
        TrackedResource resource =
                new TrackedResource(ids.incrementAndGet(), type, description, CallSite.capture(), Instant.now());
        active.put(resource.id(), resource);
        return resource;
    }

    /** Registers a resource and returns an {@link AutoCloseable} lease for try-with-resources. */
    public ResourceLease lease(ResourceType type, String description) {
        return new ResourceLease(register(type, description), this);
    }

    /** Releases a previously registered resource. Idempotent. */
    public void release(TrackedResource resource) {
        if (resource != null) {
            active.remove(resource.id());
        }
    }

    /** Snapshot view of currently active resources. */
    public Set<TrackedResource> activeResources() {
        return Set.copyOf(active.values());
    }

    /** Currently active resource counts, with every type present (zero-filled). */
    public Map<ResourceType, Integer> counts() {
        Map<ResourceType, Integer> counts = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            counts.put(type, 0);
        }
        for (TrackedResource resource : active.values()) {
            counts.merge(resource.type(), 1, Integer::sum);
        }
        return counts;
    }

    /** Releases everything. Mainly useful between tests. */
    public void clear() {
        active.clear();
    }

    @Override
    public String toString() {
        return active.values().stream().map(TrackedResource::toString).collect(Collectors.joining(", ", "[", "]"));
    }
}
