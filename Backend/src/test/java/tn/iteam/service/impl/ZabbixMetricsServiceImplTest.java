package tn.iteam.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import tn.iteam.adapter.zabbix.ZabbixAdapter;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.mapper.ZabbixMetricMapper;
import tn.iteam.repository.ZabbixMetricRepository;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixDataQualityService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ZabbixMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ZabbixMetricsServiceImpl(
                adapter,
                mapper,
                repository,
                availabilityService,
                dataQualityService,
                transactionTemplate
        );
    }

    @Test
    void overlappingHeavyRefreshReturnsPersistedSnapshotInsteadOfStartingAnotherRun() {
        ZabbixMetric persisted = new ZabbixMetric();
        persisted.setHostId("101");
        persisted.setItemId("cpu");
        persisted.setTimestamp(1L);

        when(repository.findAll()).thenReturn(List.of(persisted));
        when(adapter.fetchMetricsAndMap()).thenReturn(Mono.never());

        Mono<List<ZabbixMetric>> firstRun = service.fetchAndSaveMetrics();
        List<ZabbixMetric> fallback = service.fetchAndSaveMetrics().block();

        assertThat(firstRun).isNotNull();
        assertThat(fallback).containsExactly(persisted);
        verify(adapter, times(1)).fetchMetricsAndMap();
    }
}
