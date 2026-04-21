package tn.iteam.monitoring.snapshot;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Primary
public class InMemorySnapshotStore implements SnapshotStore {

    private final Map<String, StoredSnapshot<?>> snapshots = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<StoredSnapshot<T>> get(String dataset, String source) {
        return Optional.ofNullable((StoredSnapshot<T>) snapshots.get(buildKey(dataset, source)));
    }

    @Override
    public void save(String dataset, String source, StoredSnapshot<?> snapshot) {
        if (snapshot == null) {
            return;
        }
        snapshots.put(buildKey(dataset, source), snapshot);
    }

    private String buildKey(String dataset, String source) {
        return normalize(dataset) + ":" + normalize(source);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "all";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
