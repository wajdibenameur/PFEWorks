package tn.iteam.monitoring.snapshot;

import java.util.Optional;

public interface SnapshotStore {

    <T> Optional<StoredSnapshot<T>> get(String dataset, String source);

    void save(String dataset, String source, StoredSnapshot<?> snapshot);
}
