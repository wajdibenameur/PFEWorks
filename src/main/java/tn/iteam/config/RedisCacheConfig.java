package tn.iteam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheConfig.class);

    public static final String HOSTS_CACHE = "monitoring:hosts";
    public static final String METRICS_CACHE = "monitoring:metrics";
    public static final String PROBLEMS_CACHE = "monitoring:problems";
    public static final String SOURCE_HEALTH_CACHE = "monitoring:source-health";

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            @Value("${app.cache.ttl.hosts:30s}") Duration hostsTtl,
            @Value("${app.cache.ttl.metrics:30s}") Duration metricsTtl,
            @Value("${app.cache.ttl.problems:30s}") Duration problemsTtl,
            @Value("${app.cache.ttl.source-health:15s}") Duration sourceHealthTtl
    ) {
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper.copy());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer)
                )
                .disableCachingNullValues()
                .entryTtl(Duration.ofSeconds(30));

        Map<String, RedisCacheConfiguration> cacheConfigurations = new LinkedHashMap<>();
        cacheConfigurations.put(HOSTS_CACHE, defaultConfig.entryTtl(hostsTtl));
        cacheConfigurations.put(METRICS_CACHE, defaultConfig.entryTtl(metricsTtl));
        cacheConfigurations.put(PROBLEMS_CACHE, defaultConfig.entryTtl(problemsTtl));
        cacheConfigurations.put(SOURCE_HEALTH_CACHE, defaultConfig.entryTtl(sourceHealthTtl));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed for cache={} key={}: {}", cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed for cache={} key={}: {}", cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed for cache={} key={}: {}", cacheName(cache), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed for cache={}: {}", cacheName(cache), exception.getMessage());
            }

            private String cacheName(Cache cache) {
                return cache != null ? cache.getName() : "unknown";
            }
        };
    }
}
