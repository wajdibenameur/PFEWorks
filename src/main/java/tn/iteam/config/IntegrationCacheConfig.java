package tn.iteam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tn.iteam.cache.InMemoryIntegrationCacheService;
import tn.iteam.cache.IntegrationCacheService;
import tn.iteam.cache.RedisIntegrationCacheService;
import tn.iteam.cache.ResilientIntegrationCacheService;

/**
 * Configuration for IntegrationCacheService with automatic Redis fallback.
 * 
 * Behavior:
 * - If Redis is available and enabled: uses ResilientIntegrationCacheService (Redis + fallback)
 * - If Redis is disabled: uses InMemoryIntegrationCacheService directly
 * - If Redis fails at runtime: automatic fallback to in-memory
 * 
 * Configuration properties:
 * - app.cache.integration.enabled: true/false (default: true)
 * - app.cache.integration.use-redis: true/false (default: true)
 */
@Slf4j
@Configuration
public class IntegrationCacheConfig {

    /**
     * Redis-based cache service (primary implementation).
     * Only created if Redis is enabled.
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.integration.use-redis", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "redisIntegrationCacheService")
    public IntegrationCacheService redisIntegrationCacheService(
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        log.info("Configuring Redis-based IntegrationCacheService");
        return new RedisIntegrationCacheService(redisTemplate, objectMapper);
    }

    /**
     * In-memory fallback cache service.
     * Always available as fallback or when Redis is disabled.
     */
    @Bean
    @ConditionalOnMissingBean(name = "inMemoryIntegrationCacheService")
    public IntegrationCacheService inMemoryIntegrationCacheService(ObjectMapper objectMapper) {
        log.info("Configuring In-Memory IntegrationCacheService (fallback)");
        return new InMemoryIntegrationCacheService(objectMapper);
    }

    /**
     * Resilient cache service that wraps Redis with automatic fallback.
     * This is the PRIMARY bean used by the application.
     * 
     * If Redis is disabled via config, falls back to in-memory directly.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.cache.integration.enabled", havingValue = "true", matchIfMissing = true)
    public IntegrationCacheService integrationCacheService(
            @Qualifier("redisIntegrationCacheService") IntegrationCacheService redisService,
            @Qualifier("inMemoryIntegrationCacheService") IntegrationCacheService inMemoryService,
            org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
            @Value("${app.cache.integration.use-redis:true}") boolean useRedis
    ) {
        if (useRedis) {
            log.info("Configuring ResilientIntegrationCacheService (Redis + In-Memory fallback)");
            return new ResilientIntegrationCacheService(redisService, inMemoryService, redisTemplate);
        } else {
            log.info("Redis disabled - using InMemoryIntegrationCacheService directly");
            return inMemoryService;
        }
    }

    /**
     * Fallback bean when cache is completely disabled.
     */
    @Bean
    @ConditionalOnProperty(name = "app.cache.integration.enabled", havingValue = "false")
    @Primary
    public IntegrationCacheService disabledIntegrationCacheService() {
        log.warn("Integration cache DISABLED - using no-op implementation");
        return new IntegrationCacheService() {
            @Override
            public void saveSnapshot(String source, String key, Object data) {
                log.debug("Cache disabled - ignoring save: {}/{}", source, key);
            }

            @Override
            public <T> java.util.Optional<T> getSnapshot(String source, String key, Class<T> type) {
                log.debug("Cache disabled - returning empty: {}/{}", source, key);
                return java.util.Optional.empty();
            }
        };
    }
}