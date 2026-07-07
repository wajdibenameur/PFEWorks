package tn.iteam.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.iteam.config.SecurityConfig;
import tn.iteam.integration.AsyncIntegrationService;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.security.PermissionService;
import tn.iteam.service.SnmpInterfaceService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringFreshnessService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitoringController.class)
@Import({
        SecurityConfig.class,
        PermissionService.class
})
class MonitoringControllerSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MonitoringAggregationService aggregationService;

    @MockBean
    private SourceAvailabilityService sourceAvailabilityService;

    @MockBean
    private IntegrationServiceRegistry integrationServiceRegistry;

    @MockBean
    private AsyncIntegrationService asyncIntegrationService;

    @MockBean
    private MonitoringSnapshotPublicationService snapshotPublicationService;

    @MockBean
    private SnmpInterfaceService snmpInterfaceService;

    @MockBean
    private MonitoringFreshnessService monitoringFreshnessService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter;

    @Test
    void monitoringMetricsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/monitoring/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void collectEndpointRejectsRoleWithoutRefreshPermission() throws Exception {
        mockMvc.perform(post("/api/monitoring/collect")
                        .with(jwt().authorities(() -> "VIEW_DASHBOARD")))
                .andExpect(status().isForbidden());
    }

    @Test
    void collectEndpointAllowsUserWithRefreshPermission() throws Exception {
        when(integrationServiceRegistry.getRequired(any(MonitoringSourceType.class)))
                .thenReturn(asyncIntegrationService);

        when(asyncIntegrationService.refreshAsync())
                .thenReturn(Mono.empty());

        mockMvc.perform(post("/api/monitoring/collect")
                        .with(jwt().authorities(() -> "REFRESH_DASHBOARD")))
                .andExpect(status().isOk());
    }
}
