package tn.iteam.monitoring.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.provider.MonitoringProvider;
import tn.iteam.service.SourceAvailabilityService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = MonitoringCacheServiceCachingTest.TestConfig.class)
class MonitoringCacheServiceCachingTest {

    @org.springframework.beans.factory.annotation.Autowired
    private MonitoringCacheService monitoringCacheService;

    @org.springframework.beans.factory.annotation.Autowired
    private ToggleableProblemProvider provider;

    @BeforeEach
    void setUp() {
        provider.reset();
    }

    @Test
    void degradedAggregationIsNotCachedAndHealthyResultIsCachedAfterRecovery() {
        provider.failProblems.set(true);

        MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> degraded =
                monitoringCacheService.getProblems("ZABBIX");

        assertThat(degraded.isDegraded()).isTrue();
        assertThat(degraded.getData()).isEmpty();
        assertThat(provider.problemCalls.get()).isEqualTo(1);

        provider.failProblems.set(false);

        MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> recovered =
                monitoringCacheService.getProblems("ZABBIX");

        assertThat(recovered.isDegraded()).isFalse();
        assertThat(recovered.getData()).hasSize(1);
        assertThat(provider.problemCalls.get()).isEqualTo(2);

        MonitoringCacheService.FetchResult<List<UnifiedMonitoringProblemDTO>> cached =
                monitoringCacheService.getProblems("ZABBIX");

        assertThat(cached.isDegraded()).isFalse();
        assertThat(cached.getData()).hasSize(1);
        assertThat(provider.problemCalls.get()).isEqualTo(2);
    }

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("monitoring:problems", "monitoring:metrics", "monitoring:hosts");
        }

        @Bean
        ToggleableProblemProvider toggleableProblemProvider() {
            return new ToggleableProblemProvider();
        }

        @Bean
        SourceAvailabilityService sourceAvailabilityService() {
            return Mockito.mock(SourceAvailabilityService.class);
        }

        @Bean
        MonitoringCacheService monitoringCacheService(
                List<MonitoringProvider> providers,
                SourceAvailabilityService sourceAvailabilityService
        ) {
            return new MonitoringCacheService(providers, sourceAvailabilityService);
        }
    }

    static class ToggleableProblemProvider implements MonitoringProvider {
        final AtomicBoolean failProblems = new AtomicBoolean(false);
        final AtomicInteger problemCalls = new AtomicInteger(0);

        void reset() {
            failProblems.set(false);
            problemCalls.set(0);
        }

        @Override
        public MonitoringSourceType getSourceType() {
            return MonitoringSourceType.ZABBIX;
        }

        @Override
        public List<tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO> getHosts() {
            return List.of();
        }

        @Override
        public List<UnifiedMonitoringProblemDTO> getProblems() {
            problemCalls.incrementAndGet();
            if (failProblems.get()) {
                throw new IllegalStateException("provider down");
            }
            return List.of(
                    UnifiedMonitoringProblemDTO.builder()
                            .id("p1")
                            .source(MonitoringSourceType.ZABBIX)
                            .problemId("p1")
                            .hostName("host-1")
                            .startedAt(1L)
                            .build()
            );
        }

        @Override
        public List<tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO> getMetrics() {
            return List.of();
        }
    }
}
