package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers requirement 1: snapshot capture and replaceable resource providers. */
class ResourceSnapshotTest {

    @Test
    void registryCountsReflectRegisterAndRelease() {
        ResourceRegistry registry = new ResourceRegistry();
        TrackedResource thread = registry.register(ResourceType.THREAD, "worker-1");
        registry.register(ResourceType.CONNECTION, "jdbc:mock://db");

        Map<ResourceType, Integer> counts = registry.counts();

        assertThat(counts).containsEntry(ResourceType.THREAD, 1)
                .containsEntry(ResourceType.CONNECTION, 1)
                .containsEntry(ResourceType.FILE_HANDLE, 0);

        registry.release(thread);
        assertThat(registry.counts()).containsEntry(ResourceType.THREAD, 0);
    }

    @Test
    void leaseReleasesResourceOnClose() {
        ResourceRegistry registry = new ResourceRegistry();
        try (ResourceLease lease = registry.lease(ResourceType.FILE_HANDLE, "/tmp/data.bin")) {
            assertThat(registry.counts()).containsEntry(ResourceType.FILE_HANDLE, 1);
            assertThat(lease.resource().description()).isEqualTo("/tmp/data.bin");
        }
        assertThat(registry.counts()).containsEntry(ResourceType.FILE_HANDLE, 0);
    }

    @Test
    void snapshotCapturesCountsAndResources() {
        ResourceRegistry registry = new ResourceRegistry();
        registry.register(ResourceType.THREAD, "t1");
        registry.register(ResourceType.THREAD, "t2");

        ResourceSnapshot snapshot = new LeakDetector(new RegistryBackedProvider(registry)).snapshot();

        assertThat(snapshot.count(ResourceType.THREAD)).isEqualTo(2);
        assertThat(snapshot.count(ResourceType.CONNECTION)).isZero();
        assertThat(snapshot.resourcesOf(ResourceType.THREAD)).hasSize(2);
        assertThat(snapshot.takenAt()).isNotNull();
    }

    @Test
    void providerIsReplaceableWithASimulation() {
        ResourceProvider fakeProvider = () -> Map.of(
                ResourceType.THREAD, 7,
                ResourceType.FILE_HANDLE, 3);
        LeakDetector detector = new LeakDetector(fakeProvider);

        ResourceSnapshot snapshot = detector.snapshot();

        assertThat(snapshot.count(ResourceType.THREAD)).isEqualTo(7);
        assertThat(snapshot.count(ResourceType.FILE_HANDLE)).isEqualTo(3);
        assertThat(snapshot.count(ResourceType.CONNECTION)).isZero();
    }

    @Test
    void snapshotDeltaIsComputedPerType() {
        ResourceSnapshot before = ResourceSnapshot.of(Map.of(ResourceType.THREAD, 2), null);
        ResourceSnapshot after = ResourceSnapshot.of(
                Map.of(ResourceType.THREAD, 5, ResourceType.CONNECTION, 1), null);

        assertThat(after.delta(ResourceType.THREAD, before)).isEqualTo(3);
        assertThat(after.delta(ResourceType.CONNECTION, before)).isEqualTo(1);
        assertThat(after.delta(ResourceType.FILE_HANDLE, before)).isZero();
    }
}
