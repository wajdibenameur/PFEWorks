package tn.iteam.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback implementation for IntegrationCacheService.
 * Used when Redis is unavailable or not configured.
 * Thread-safe using ConcurrentHashMap.
 * Managed by IntegrationCacheConfig.
 */
@Slf4j
@Component("inMemoryIntegrationCacheService")
public class InMemoryIntegrationCacheService implements IntegrationCacheService {

    private final ObjectMapper objectMapper;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public InMemoryIntegrationCacheService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void saveSnapshot(String source, String key, Object data) {
        if (data == null) {
            return;
        }

        String cacheKey = buildKey(source, key);

        try {
            String payload = objectMapper.writeValueAsString(data);
            cache.put(cacheKey, new CacheEntry(payload, Instant.now().plusSeconds(3600))); // 1 hour TTL
            log.debug("Saved in-memory snapshot for key: {}", cacheKey);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize in-memory snapshot for key {}: {}", cacheKey, exception.getMessage());
        }
    }

    @Override
    public <T> Optional<T> getSnapshot(String source, String key, Class<T> type) {
        String cacheKey = buildKey(source, key);

        try {
            CacheEntry entry = cache.get(cacheKey);
            if (entry == null) {
                return Optional.empty();
            }

            // Check TTL
            if (Instant.now().isAfter(entry.expiresAt)) {
                cache.remove(cacheKey);
                log.debug("In-memory snapshot expired for key: {}", cacheKey);
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(entry.payload, type));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to deserialize in-memory snapshot for key {}: {}", cacheKey, exception.getMessage());
        }

        return Optional.empty();
    }

    private String buildKey(String source, String key) {
        String safeSource = source != null ? source.trim().toLowerCase() : "unknown";
        String safeKey = key != null ? key.trim().toLowerCase() : "unknown";
        return safeSource + ":" + safeKey;
    }

    private record CacheEntry(String payload, Instant expiresAt) {
    }
}