package tn.iteam.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.iteam.config.SecurityConfig;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.security.KeycloakRolePermissionService;
import tn.iteam.security.EffectiveUserPermissionService;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.PermissionService;
import tn.iteam.service.CameraInventoryService;
import tn.iteam.service.DashboardService;
import tn.iteam.service.SnmpSummaryService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.integration.ZkBioIntegrationOperations;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
        DashboardController.class,
        SnmpController.class,
        CameraController.class,
        ZkBioController.class
})
@Import({
        SecurityConfig.class,
        KeycloakJwtAuthenticationConverter.class,
        KeycloakRolePermissionService.class,
        PermissionService.class
})
class MonitoringModuleAuthorizationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private SnmpSummaryService snmpSummaryService;

    @MockBean
    private CameraInventoryService cameraInventoryService;

    @MockBean
    private ZkBioServiceInterface zkBioService;

    @MockBean
    private ZkBioIntegrationOperations zkBioIntegrationOperations;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private EffectiveUserPermissionService effectiveUserPermissionService;

    @MockBean
    private AuthenticatedUserService authenticatedUserService;

    @Test
    void dashboardOverviewRequiresDashboardPermission() throws Exception {
        mockMvc.perform(get("/dashboard/overview"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/dashboard/overview").with(jwt().authorities(() -> "VIEW_ZABBIX")))
                .andExpect(status().isForbidden());

        when(dashboardService.getOverview()).thenReturn(null);
        mockMvc.perform(get("/dashboard/overview").with(jwt().authorities(() -> "VIEW_DASHBOARD")))
                .andExpect(status().isOk());
    }

    @Test
    void snmpSummaryRequiresSnmpPermission() throws Exception {
        mockMvc.perform(get("/api/snmp/summary").with(jwt().authorities(() -> "VIEW_DASHBOARD")))
                .andExpect(status().isForbidden());

        when(snmpSummaryService.getSummary()).thenReturn(Map.of());
        mockMvc.perform(get("/api/snmp/summary").with(jwt().authorities(() -> "VIEW_SNMP")))
                .andExpect(status().isOk());
    }

    @Test
    void cameraInventoryRequiresCameraPermission() throws Exception {
        mockMvc.perform(get("/api/cameras").with(jwt().authorities(() -> "VIEW_DASHBOARD")))
                .andExpect(status().isForbidden());

        when(cameraInventoryService.getRegisteredCameras()).thenReturn(List.of());
        mockMvc.perform(get("/api/cameras").with(jwt().authorities(() -> "VIEW_CAMERA")))
                .andExpect(status().isOk());
    }

    @Test
    void zkbioStatusRequiresZkBioPermission() throws Exception {
        mockMvc.perform(get("/api/zkbio/status").with(jwt().authorities(() -> "VIEW_DASHBOARD")))
                .andExpect(status().isForbidden());

        when(zkBioService.getServerStatus()).thenReturn(null);
        mockMvc.perform(get("/api/zkbio/status").with(jwt().authorities(() -> "VIEW_ZKBIO")))
                .andExpect(status().isOk());
    }
}
