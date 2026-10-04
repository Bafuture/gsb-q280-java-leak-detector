package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallSiteTrackingTest {

    private final ResourceRegistry registry = new ResourceRegistry();

    @Test
    void recordsCallSiteOfResourceCreation() {
        TrackedResource resource = acquireConnection("jdbc:simulated");

        assertThat(resource.creationSite().getClassName())
                .isEqualTo(CallSiteTrackingTest.class.getName());
        assertThat(resource.creationSite().getMethodName())
                .isEqualTo("acquireConnection");
        assertThat(resource.creationSite().getLineNumber()).isPositive();
    }

    @Test
    void releaseRemovesResourceFromLiveSetAndIsIdempotent() {
        TrackedResource resource = registry.register(ResourceType.CONNECTION, "conn-1");
        assertThat(registry.liveCount(ResourceType.CONNECTION)).isEqualTo(1);

        resource.release();
        resource.release();

        assertThat(registry.liveCount(ResourceType.CONNECTION)).isZero();
        assertThat(registry.liveResources(ResourceType.CONNECTION)).isEmpty();
        assertThat(resource.isReleased()).isTrue();
    }

    @Test
    void exposesOnlyLiveResourcesOfRequestedType() {
        TrackedResource live = registry.register(ResourceType.CONNECTION, "still-open");
        registry.register(ResourceType.CONNECTION, "closed").release();
        registry.register(ResourceType.FILE_HANDLE, "tmp-file");

        assertThat(registry.liveResources(ResourceType.CONNECTION)).containsExactly(live);
        live.release();
    }

    private TrackedResource acquireConnection(String description) {
        return registry.register(ResourceType.CONNECTION, description);
    }
}
