package tn.iteam.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.Logger;
import tn.iteam.adapter.zabbix.ZabbixCriticalEventHistoryCollector;
import tn.iteam.domain.ZabbixProblem;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.mapper.ZabbixProblemMapper;
import tn.iteam.repository.ZabbixOldProblemRepository;
import tn.iteam.repository.ZabbixProblemRepository;
import tn.iteam.service.ZabbixCriticalHistorySyncResult;
import tn.iteam.service.support.ZabbixProblemSanitizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZabbixCriticalHistorySyncServiceImplTest {

    @Mock
    private ZabbixCriticalEventHistoryCollector collector;

    @Mock
    private ZabbixProblemRepository problemRepository;

    @Mock
    private ZabbixOldProblemRepository oldProblemRepository;

    @Mock
    private ZabbixProblemMapper problemMapper;

    @Mock
    private ZabbixProblemSanitizer problemSanitizer;

    private ZabbixCriticalHistorySyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ZabbixCriticalHistorySyncServiceImpl(
                collector,
                problemRepository,
                oldProblemRepository,
                problemMapper,
                problemSanitizer
        );
    }

    @Test
    void syncCriticalHistoryReturnsDisabledWithoutTouchingCollectors() {
        ReflectionTestUtils.setField(service, "enabled", false);

        ZabbixCriticalHistorySyncResult result = service.syncCriticalHistory();

        assertThat(result.enabled()).isFalse();
        assertThat(result.found()).isZero();
        verify(collector, never()).collectCriticalHistory();
        verify(problemRepository, never()).saveAll(any());
        verify(oldProblemRepository, never()).saveAll(any());
    }

    @Test
    void syncCriticalHistorySkipsDuplicatesAndPersistsOnlyNewEvents() {
        ReflectionTestUtils.setField(service, "enabled", true);

        ZabbixProblemDTO duplicateDto = ZabbixProblemDTO.builder()
                .problemId("1001")
                .eventId(1001L)
                .hostId("10767")
                .host("ACCESS-CONTROL")
                .description("Existing")
                .severity("4")
                .active(false)
                .status("RESOLVED")
                .startedAt(10L)
                .build();
        ZabbixProblemDTO freshDto = ZabbixProblemDTO.builder()
                .problemId("1002")
                .eventId(1002L)
                .hostId("10768")
                .host("SRV01")
                .description("Fresh")
                .severity("5")
                .active(false)
                .status("RESOLVED")
                .startedAt(20L)
                .build();
        ZabbixProblem entity = ZabbixProblem.builder()
                .problemId("1002")
                .eventId(1002L)
                .hostId(10768L)
                .host("SRV01")
                .description("Fresh")
                .severity("5")
                .active(false)
                .status("RESOLVED")
                .startedAt(20L)
                .build();

        when(collector.collectCriticalHistory()).thenReturn(List.of(duplicateDto, freshDto, freshDto));
        when(oldProblemRepository.existsByEventId(1001L)).thenReturn(true);
        when(oldProblemRepository.existsByEventId(1002L)).thenReturn(false);
        when(problemSanitizer.sanitize(org.mockito.ArgumentMatchers.eq(freshDto), any(Logger.class))).thenReturn(freshDto);
        when(problemMapper.toEntity(freshDto)).thenReturn(entity);

        ZabbixCriticalHistorySyncResult result = service.syncCriticalHistory();

        ArgumentCaptor<List> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(problemRepository).saveAll(savedCaptor.capture());
        verify(problemRepository).flush();
        verify(oldProblemRepository).saveAll(anyList());
        verify(oldProblemRepository).flush();
        assertThat(savedCaptor.getValue()).containsExactly(entity);
        assertThat(result.enabled()).isTrue();
        assertThat(result.found()).isEqualTo(3);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.duplicatesIgnored()).isEqualTo(2);
        assertThat(result.invalidIgnored()).isZero();
    }
}
