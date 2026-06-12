package tn.iteam.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import tn.iteam.adapter.snmp.SnmpDeviceSnapshot;
import tn.iteam.config.SecurityConfig;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.enums.DeviceStatus;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.security.EffectiveUserPermissionService;
import tn.iteam.security.KeycloakJwtAuthenticationConverter;
import tn.iteam.security.KeycloakRolePermissionService;
import tn.iteam.security.PermissionService;
import tn.iteam.service.SnmpMonitoringService;
import tn.iteam.service.SnmpSummaryService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SnmpController.class)
@Import({
        SecurityConfig.class,
        KeycloakJwtAuthenticationConverter.class,
        KeycloakRolePermissionService.class,
        PermissionService.class
})
class SnmpControllerSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SnmpSummaryService snmpSummaryService;

    @MockBean
    private SnmpMonitoringService snmpMonitoringService;

    @MockBean
    private SnmpDeviceRepository snmpDeviceRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private EffectiveUserPermissionService effectiveUserPermissionService;

    @MockBean
    private AuthenticatedUserService authenticatedUserService;

    @Test
    void snmpSummaryRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/snmp/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void snmpTestAllowsUserWithViewSnmpPermission() throws Exception {
        SnmpDevice device = new SnmpDevice();
        device.setIpAddress("192.168.130.20");
        device.setSnmpPort(161);
        device.setSnmpCommunity("public");
        device.setSnmpVersion("2c");
        device.setCategory("Printers");
        device.setEnabled(true);

        SnmpDeviceSnapshot snapshot = SnmpDeviceSnapshot.builder()
                .ipAddress("192.168.130.20")
                .hostId("192.168.130.20")
                .hostName("PRT-XR-A-3-FCLT")
                .category("Printers")
                .snmpPort(161)
                .status("UP")
                .deviceStatus(DeviceStatus.UP)
                .availability(1.0)
                .cpuPercent(null)
                .memoryPercent(null)
                .uptimeSeconds(3600L)
                .sysDescr("Xerox Printer")
                .interfaces(List.of())
                .extraMetrics(Map.of())
                .collectedAtEpochSec(1_780_998_000L)
                .build();

        when(snmpDeviceRepository.findByIpAddress("192.168.130.20")).thenReturn(Optional.of(device));
        when(snmpMonitoringService.pollSingleDevice(any(SnmpDevice.class))).thenReturn(snapshot);

        mockMvc.perform(post("/api/snmp/test")
                        .param("ip", "192.168.130.20")
                        .with(jwt().authorities(() -> "VIEW_SNMP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("SNMP DEVICE TEST COMPLETED"))
                .andExpect(jsonPath("$.data.ipAddress").value("192.168.130.20"))
                .andExpect(jsonPath("$.data.foundInInventory").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.hostName").value("PRT-XR-A-3-FCLT"));
    }
}
