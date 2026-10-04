package com.example.gsb.leakdetector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceSnapshotTest {

    @Test
    void exposesCountsPerTypeAndDefaultsMissingTypesToZero() {
        ResourceSnapshot snapshot = ResourceSnapshot.of(Map.of(
                ResourceType.THREAD, 3,
                ResourceType.CONNECTION, 1));

        assertThat(snapshot.count(ResourceType.THREAD)).isEqualTo(3);
        assertThat(snapshot.count(ResourceType.CONNECTION)).isEqualTo(1);
        assertThat(snapshot.count(ResourceType.FILE_HANDLE)).isZero();
    }

    @Test
    void computesSignedDeltaAgainstBaseline() {
        ResourceSnapshot before = ResourceSnapshot.of(Map.of(
                ResourceType.THREAD, 2,
                ResourceType.FILE_HANDLE, 4,
                ResourceType.CONNECTION, 1));
        ResourceSnapshot after = ResourceSnapshot.of(Map.of(
                ResourceType.THREAD, 3,
                ResourceType.FILE_HANDLE, 2,
                ResourceType.CONNECTION, 1));

        Map<ResourceType, Integer> delta = after.delta(before);

        assertThat(delta)
                .containsEntry(ResourceType.THREAD, 1)
                .containsEntry(ResourceType.FILE_HANDLE, -2)
                .containsEntry(ResourceType.CONNECTION, 0);
    }

    @Test
    void providerAssemblesSnapshotFromSuppliers() {
        int[] threads = {7};
        ResourceProvider provider = ResourceProviders.of(Map.of(
                ResourceType.THREAD, () -> threads[0]));

        assertThat(provider.snapshot().count(ResourceType.THREAD)).isEqualTo(7);
        threads[0] = 9;
        assertThat(provider.snapshot().count(ResourceType.THREAD)).isEqualTo(9);
    }
}
