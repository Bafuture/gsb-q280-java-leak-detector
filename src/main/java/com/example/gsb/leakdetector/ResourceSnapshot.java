package com.example.gsb.leakdetector;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Immutable point-in-time observation of all watched resources. */
public final class ResourceSnapshot {

    private final Instant takenAt;
    private final Map<ResourceType, Integer> counts;
    private final Set<TrackedResource> resources;

    private ResourceSnapshot(Instant takenAt, Map<ResourceType, Integer> counts, Set<TrackedResource> resources) {
        this.takenAt = takenAt;
        this.counts = counts;
        this.resources = resources;
    }

    /** Creates a snapshot; missing types are zero-filled and inputs are defensively copied. */
    public static ResourceSnapshot of(Map<ResourceType, Integer> counts, Set<TrackedResource> resources) {
        Map<ResourceType, Integer> normalized = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            normalized.put(type, counts == null ? 0 : counts.getOrDefault(type, 0));
        }
        return new ResourceSnapshot(
                Instant.now(),
                Collections.unmodifiableMap(normalized),
                resources == null ? Set.of() : Set.copyOf(resources));
    }

    public Instant takenAt() {
        return takenAt;
    }

    public int count(ResourceType type) {
        return counts.getOrDefault(type, 0);
    }

    public Map<ResourceType, Integer> counts() {
        return counts;
    }

    public Set<TrackedResource> resources() {
        return resources;
    }

    public Set<TrackedResource> resourcesOf(ResourceType type) {
        return resources.stream().filter(r -> r.type() == type).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Signed count delta of this snapshot relative to an earlier one. */
    public int delta(ResourceType type, ResourceSnapshot earlier) {
        return count(type) - earlier.count(type);
    }
}
