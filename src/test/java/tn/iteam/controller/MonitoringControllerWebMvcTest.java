package tn.iteam.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tn.iteam.integration.CameraIntegrationService;
import tn.iteam.integration.ObserviumIntegrationService;
import tn.iteam.integration.ZabbixIntegrationService;
import tn.iteam.integration.ZkBioIntegrationService;
import tn.iteam.monitoring.dto.UnifiedMonitoringHostDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringResponse;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MonitoringControllerWebMvcTest {

    private MockMvc mockMvc;

    @Mock
    private CameraIntegrationService cameraIntegrationService;

    @Mock
    private MonitoringAggregationService aggregationService;

    @Mock
    private SourceAvailabilityService sourceAvailabilityService;

    @Mock
    private ZabbixIntegrationService zabbixIntegrationService;

    @Mock
    private ObserviumIntegrationService observiumIntegrationService;

    @Mock
    private ZkBioIntegrationService zkBioIntegrationService;

    @Mock
    private MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Mock
    private ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @BeforeEach
    void setUp() {
        MonitoringController controller = new MonitoringController(
                cameraIntegrationService,
                aggregationService,
                sourceAvailabilityService,
                zabbixIntegrationService,
                observiumIntegrationService,
                zkBioIntegrationService,
                monitoringWebSocketPublisher,
                zkBioWebSocketPublisher
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void problemsEndpointReturnsUnifiedMonitoringResponseWithDegradedAndFreshness() throws Exception {
        when(aggregationService.getProblems((String) null)).thenReturn(
                new UnifiedMonitoringResponse<>(
                        List.of(
                                UnifiedMonitoringProblemDTO.builder()
                                        .id("OBSERVIUM:p1")
                                        .problemId("p1")
                                        .description("Fallback problem")
                                        .build()
                        ),
                        true,
                        Map.of("OBSERVIUM", "redis_fallback"),
                        Map.of()
                )
        );

        mockMvc.perform(get("/api/monitoring/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.data[0].problemId").value("p1"))
                .andExpect(jsonPath("$.freshness.OBSERVIUM").value("redis_fallback"))
                .andExpect(jsonPath("$.coverage").isMap());

        verify(aggregationService).getProblems((String) null);
    }

    @Test
    void metricsEndpointReturnsUnifiedMonitoringResponseWithCoverage() throws Exception {
        when(aggregationService.getMetrics((String) null)).thenReturn(
                new UnifiedMonitoringResponse<>(
                        List.of(
                                UnifiedMonitoringMetricDTO.builder()
                                        .id("ZABBIX:m1")
                                        .itemId("1001")
                                        .metricKey("system.cpu.util")
                                        .build()
                        ),
                        false,
                        Map.of("ZABBIX", "persisted"),
                        Map.of(
                                "ZABBIX", "native",
                                "OBSERVIUM", "synthetic",
                                "ZKBIO", "synthetic",
                                "CAMERA", "not_applicable"
                        )
                )
        );

        mockMvc.perform(get("/api/monitoring/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.data[0].metricKey").value("system.cpu.util"))
                .andExpect(jsonPath("$.freshness.ZABBIX").value("persisted"))
                .andExpect(jsonPath("$.coverage.ZABBIX").value("native"))
                .andExpect(jsonPath("$.coverage.OBSERVIUM").value("synthetic"))
                .andExpect(jsonPath("$.coverage.ZKBIO").value("synthetic"))
                .andExpect(jsonPath("$.coverage.CAMERA").value("not_applicable"));

        verify(aggregationService).getMetrics((String) null);
    }

    @Test
    void hostsEndpointReturnsUnifiedMonitoringResponseWrapper() throws Exception {
        when(aggregationService.getHosts((String) null)).thenReturn(
                new UnifiedMonitoringResponse<>(
                        List.of(
                                UnifiedMonitoringHostDTO.builder()
                                        .id("ZABBIX:h1")
                                        .hostId("h1")
                                        .name("host-1")
                                        .build()
                        ),
                        false,
                        Map.of("ZABBIX", "persisted"),
                        Map.of()
                )
        );

        mockMvc.perform(get("/api/monitoring/hosts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.data[0].hostId").value("h1"))
                .andExpect(jsonPath("$.freshness.ZABBIX").value("persisted"))
                .andExpect(jsonPath("$.coverage").isMap());

        verify(aggregationService).getHosts((String) null);
    }
}
