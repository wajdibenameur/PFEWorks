package tn.iteam.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.adapter.zabbix.ZabbixMetricsCollectionResult;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixMetricDTO;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixDataQualityService;
import tn.iteam.service.support.DatabasePersistenceGuard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZabbixMetricsServiceImplTest {

    @Mock
    private ZabbixAdapter adapter;

    @Mock
    private ZabbixMetricMapper mapper;

    @Mock
    private ZabbixMetricRepository repository;

    @Mock
    private SourceAvailabilityService availabilityService;

    @Mock
    private ZabbixDataQualityService dataQualityService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private DatabasePersistenceGuard databasePersistenceGuard;

    private ZabbixMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ZabbixMetricsServiceImpl(
                adapter,
                mapper,
                repository,
                availabilityService,
                dataQualityService,
                transactionTemplate,
                databasePersistenceGuard
        );
    }

    @Test
    void overlappingHeavyRefreshReturnsPersistedSnapshotInsteadOfStartingAnotherRun() throws InterruptedException {
        ZabbixMetric persisted = new ZabbixMetric();
        persisted.setHostId("101");
        persisted.setItemId("cpu");
        persisted.setTimestamp(1L);
        ZabbixMetricDTO dto = ZabbixMetricDTO.builder()
                .hostId("101")
                .itemId("cpu")
                .metricKey("system.cpu.util")
                .value(42.0d)
                .valueType(0)
                .status("0")
                .timestamp(2L)
                .build();
        ZabbixMetric freshMetric = new ZabbixMetric();
        freshMetric.setHostId("101");
        freshMetric.setItemId("cpu");
        freshMetric.setTimestamp(2L);

        CountDownLatch firstRunEnteredPersistence = new CountDownLatch(1);
        CountDownLatch releaseFirstRun = new CountDownLatch(1);

        when(repository.findLatestByHostAndItem()).thenReturn(List.of(persisted));
        when(repository.findAllByHostIdInAndItemIdInAndTimestampIn(any(), any(), any())).thenReturn(List.of());
        when(adapter.fetchMetricsCollection()).thenReturn(Mono.just(new ZabbixMetricsCollectionResult(List.of(dto), false)));
        when(mapper.toEntity(dto)).thenReturn(freshMetric);
        when(databasePersistenceGuard.safeRun(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            firstRunEnteredPersistence.countDown();
            try {
                if (!releaseFirstRun.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release first refresh");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting to release first refresh", exception);
            }
            action.run();
            return true;
        });
        when(transactionTemplate.execute(any())).thenReturn(new ArrayList<>());

        Mono<List<ZabbixMetric>> firstRun = service.fetchAndSaveMetrics();
        Mono<List<ZabbixMetric>> blockedFirstRun = firstRun.cache();
        blockedFirstRun.subscribe();

        assertThat(firstRunEnteredPersistence.await(2, TimeUnit.SECONDS)).isTrue();

        List<ZabbixMetric> fallback = service.fetchAndSaveMetrics().block();
        releaseFirstRun.countDown();

        assertThat(blockedFirstRun).isNotNull();
        assertThat(fallback).containsExactly(persisted);
        verify(adapter, times(2)).fetchMetricsCollection();
    }

    @Test
    void partialMetricsCollection_stillPersistsValidRows() {
        ZabbixMetricDTO dto = ZabbixMetricDTO.builder()
                .hostId("101")
                .hostName("host-101")
                .itemId("cpu")
                .metricName("CPU")
                .metricKey("system.cpu.util")
                .value(42.0d)
                .valueType(0)
                .status("UP")
                .source("Zabbix")
                .timestamp(2L)
                .build();

        ZabbixMetric mapped = new ZabbixMetric();
        mapped.setHostId("101");
        mapped.setHostName("host-101");
        mapped.setItemId("cpu");
        mapped.setMetricName("CPU");
        mapped.setMetricKey("system.cpu.util");
        mapped.setValue(42.0d);
        mapped.setValueType(0);
        mapped.setStatus("UP");
        mapped.setSource("Zabbix");
        mapped.setTimestamp(2L);

        when(repository.findAllByHostIdInAndItemIdInAndTimestampIn(any(), any(), any())).thenReturn(List.of());
        when(repository.findLatestByHostAndItem()).thenReturn(List.of());
        when(adapter.fetchMetricsCollection()).thenReturn(Mono.just(new ZabbixMetricsCollectionResult(List.of(dto), true)));
        when(mapper.toEntity(dto)).thenReturn(mapped);
        when(databasePersistenceGuard.safeRun(eq("ZABBIX"), eq("metrics-persistence"), any())).thenAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return true;
        });
        when(transactionTemplate.execute(any())).thenReturn(List.of(mapped));

        List<ZabbixMetric> persisted = service.fetchAndSaveMetrics().block();

        assertThat(persisted).containsExactly(mapped);
        verify(databasePersistenceGuard).safeRun(eq("ZABBIX"), eq("metrics-persistence"), any());
        verify(transactionTemplate).execute(any());
    }
}
