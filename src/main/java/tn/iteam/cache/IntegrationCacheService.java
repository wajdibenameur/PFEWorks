package tn.iteam.cache;

import java.util.Optional;

public interface IntegrationCacheService {

    void saveSnapshot(String source, String key, Object data);

    <T> Optional<T> getSnapshot(String source, String key, Class<T> type);
}
