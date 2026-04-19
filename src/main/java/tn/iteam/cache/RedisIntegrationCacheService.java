package tn.iteam.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisIntegrationCacheService implements IntegrationCacheService {

    private static final String KEY_PREFIX = "integration:snapshot";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cache.ttl.integration-snapshot:60s}")
    private Duration snapshotTtl;

    @Override
    public void saveSnapshot(String source, String key, Object data) {
        if (data == null) {
            return;
        }

        String redisKey = buildKey(source, key);

        try {
            String payload = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(redisKey, payload, snapshotTtl);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize integration snapshot for key {}: {}", redisKey, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Unable to save integration snapshot for key {}: {}", redisKey, exception.getMessage());
        }
    }

    @Override
    public <T> Optional<T> getSnapshot(String source, String key, Class<T> type) {
        String redisKey = buildKey(source, key);

        try {
            String payload = redisTemplate.opsForValue().get(redisKey);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(objectMapper.readValue(payload, type));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to deserialize integration snapshot for key {}: {}", redisKey, exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Unable to read integration snapshot for key {}: {}", redisKey, exception.getMessage());
        }

        return Optional.empty();
    }

    private String buildKey(String source, String key) {
        String safeSource = normalize(source);
        String safeKey = normalize(key);
        return KEY_PREFIX + ":" + safeSource + ":" + safeKey;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }

        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace(' ', '-')
                .replace(':', '-');
    }
}
