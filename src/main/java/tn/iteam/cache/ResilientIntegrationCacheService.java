package tn.iteam.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Resilient decorator for IntegrationCacheService that provides automatic fallback
 * from Redis to in-memory cache when Redis is unavailable.
 * 
 * This service ensures the application never blocks due to Redis failures.
 * All operations log WARN instead of throwing exceptions.
 */
@Slf4j
@Service
public class ResilientIntegrationCacheService implements IntegrationCacheService {

    private final IntegrationCacheService redisService;
    private final IntegrationCacheService inMemoryService;
    private final StringRedisTemplate redisTemplate;

    private volatile boolean redisAvailable = true;

    public ResilientIntegrationCacheService(
            @Qualifier("redisIntegrationCacheService") IntegrationCacheService redisService,
            @Qualifier("inMemoryIntegrationCacheService") IntegrationCacheService inMemoryService,
            StringRedisTemplate redisTemplate
    ) {
        this.redisService = redisService;
        this.inMemoryService = inMemoryService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void saveSnapshot(String source, String key, Object data) {
        if (isRedisAvailable()) {
            try {
                redisService.saveSnapshot(source, key, data);
                log.debug("Saved snapshot to Redis: {}/{}", source, key);
                return;
            } catch (Exception e) {
                log.warn("Redis save failed, falling back to in-memory: {}/{} - {}", source, key, e.getMessage());
                markRedisUnavailable();
            }
        }

        // Fallback to in-memory
        try {
            inMemoryService.saveSnapshot(source, key, data);
            log.debug("Saved snapshot to in-memory fallback: {}/{}", source, key);
        } catch (Exception e) {
            log.warn("In-memory fallback also failed: {}/{} - {}", source, key, e.getMessage());
        }
    }

    @Override
    public <T> Optional<T> getSnapshot(String source, String key, Class<T> type) {
        if (isRedisAvailable()) {
            try {
                Optional<T> result = redisService.getSnapshot(source, key, type);
                if (result.isPresent()) {
                    log.debug("Retrieved snapshot from Redis: {}/{}", source, key);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Redis get failed, falling back to in-memory: {}/{} - {}", source, key, e.getMessage());
                markRedisUnavailable();
            }
        }

        // Fallback to in-memory
        try {
            Optional<T> result = inMemoryService.getSnapshot(source, key, type);
            if (result.isPresent()) {
                log.debug("Retrieved snapshot from in-memory fallback: {}/{}", source, key);
            }
            return result;
        } catch (Exception e) {
            log.warn("In-memory fallback also failed: {}/{} - {}", source, key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Checks Redis availability by attempting a simple operation.
     * Uses caching to avoid repeated health checks.
     */
    private boolean isRedisAvailable() {
        if (!redisAvailable) {
            // Try to recover Redis periodically
            try {
                redisTemplate.getConnectionFactory().getConnection().ping();
                redisAvailable = true;
                log.info("Redis connection recovered");
            } catch (Exception e) {
                return false;
            }
        }
        return redisAvailable;
    }

    private void markRedisUnavailable() {
        redisAvailable = false;
        log.warn("Redis marked as unavailable - will use in-memory fallback");
    }

    /**
     * Force check Redis availability (can be called from health check endpoint).
     */
    public boolean checkRedisHealth() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            redisAvailable = true;
            log.info("Redis health check: AVAILABLE");
            return true;
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis health check: UNAVAILABLE - {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get current cache status for monitoring.
     */
    public CacheStatus getStatus() {
        return new CacheStatus(redisAvailable, isRedisAvailable());
    }

    public record CacheStatus(boolean redisAvailable, boolean usingInMemoryFallback) {
    }
}