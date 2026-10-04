package com.example.gsb.leakdetector;

import java.util.Map;
import java.util.Set;

/** Default {@link ResourceProvider} that reads from a {@link ResourceRegistry}. */
public final class RegistryBackedProvider implements ResourceProvider {

    private final ResourceRegistry registry;

    public RegistryBackedProvider(ResourceRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Map<ResourceType, Integer> activeCounts() {
        return registry.counts();
    }

    @Override
    public Set<TrackedResource> activeResources() {
        return registry.activeResources();
    }
}
