package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.SnmpDevice;
import tn.iteam.dto.SnmpDeviceTestResponseDTO;
import tn.iteam.repository.SnmpDeviceRepository;
import tn.iteam.service.SnmpMonitoringService;
import tn.iteam.service.SnmpSummaryService;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/snmp")
@RequiredArgsConstructor
@PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_SNMP)")
@Tag(name = "SNMP", description = "Endpoints de compatibilite pour SNMP")
public class SnmpController {

    private final SnmpSummaryService snmpSummaryService;
    private final SnmpMonitoringService snmpMonitoringService;
    private final SnmpDeviceRepository snmpDeviceRepository;

    @GetMapping("/summary")
    @Operation(summary = "Consulter le resume SNMP", description = "Retourne un resume agrege des donnees SNMP.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resume SNMP recupere avec succes")
    })
    public Map<String, Long> getSummary() {
        return snmpSummaryService.getSummary();
    }

    @PostMapping("/test")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_SNMP)")
    @Operation(
            summary = "Tester un equipement SNMP cible",
            description = "Lance un poll SNMP sur une seule IP pour diagnostiquer rapidement un equipement connu sans executer tout le cycle."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Test SNMP execute avec succes")
    })
    public ResponseEntity<tn.iteam.domain.ApiResponse<SnmpDeviceTestResponseDTO>> testSingleDevice(
            @RequestParam String ip,
            @RequestParam(required = false) Integer port,
            @RequestParam(required = false) String community,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String category
    ) {
        String normalizedIp = ip == null ? null : ip.trim();
        boolean foundInInventory = snmpDeviceRepository.findByIpAddress(normalizedIp).isPresent();
        SnmpDevice device = snmpDeviceRepository.findByIpAddress(normalizedIp)
                .map(existing -> applyOverrides(existing, port, community, version, category))
                .orElseGet(() -> buildAdHocDevice(normalizedIp, port, community, version, category));

        long startedAt = System.currentTimeMillis();
        var snapshot = snmpMonitoringService.pollSingleDevice(device);
        long durationMs = System.currentTimeMillis() - startedAt;

        SnmpDeviceTestResponseDTO payload = SnmpDeviceTestResponseDTO.builder()
                .ipAddress(snapshot.getIpAddress())
                .snmpPort(device.getSnmpPort())
                .snmpVersion(device.getSnmpVersion())
                .communityHint(maskCommunity(device.getSnmpCommunity()))
                .foundInInventory(foundInInventory)
                .enabled(device.getEnabled())
                .category(snapshot.getCategory())
                .status(snapshot.getStatus())
                .deviceStatus(snapshot.getDeviceStatus() != null ? snapshot.getDeviceStatus().name() : null)
                .hostName(snapshot.getHostName())
                .sysDescr(snapshot.getSysDescr())
                .uptimeSeconds(snapshot.getUptimeSeconds())
                .cpuPercent(snapshot.getCpuPercent())
                .memoryPercent(snapshot.getMemoryPercent())
                .interfaceCount(snapshot.getInterfaces() != null ? snapshot.getInterfaces().size() : 0)
                .durationMs(durationMs)
                .testedAt(Instant.now())
                .diagnosticReason(snapshot.getDiagnosticReason())
                .successfulOids(snapshot.getSuccessfulOids())
                .failedOids(snapshot.getFailedOids())
                .build();

        return ResponseEntity.ok(tn.iteam.domain.ApiResponse.<SnmpDeviceTestResponseDTO>builder()
                .success(true)
                .message("SNMP DEVICE TEST COMPLETED")
                .source("SNMP")
                .data(payload)
                .build());
    }

    private SnmpDevice applyOverrides(
            SnmpDevice source,
            Integer port,
            String community,
            String version,
            String category
    ) {
        SnmpDevice target = new SnmpDevice();
        target.setIpAddress(source.getIpAddress());
        target.setHostname(source.getHostname());
        target.setCategory(category != null && !category.isBlank() ? category.trim() : source.getCategory());
        target.setSnmpPort(port != null ? port : source.getSnmpPort());
        target.setSnmpCommunity(community != null && !community.isBlank() ? community.trim() : source.getSnmpCommunity());
        target.setSnmpVersion(version != null && !version.isBlank() ? version.trim() : source.getSnmpVersion());
        target.setEnabled(source.getEnabled());
        target.setStatus(source.getStatus());
        target.setLastSeen(source.getLastSeen());
        target.setManualEntry(source.getManualEntry());
        return target;
    }

    private SnmpDevice buildAdHocDevice(
            String ip,
            Integer port,
            String community,
            String version,
            String category
    ) {
        SnmpDevice device = new SnmpDevice();
        device.setIpAddress(ip);
        device.setHostname(ip);
        device.setCategory(category);
        device.setSnmpPort(port != null ? port : 161);
        device.setSnmpCommunity(community);
        device.setSnmpVersion(version != null && !version.isBlank() ? version.trim() : "2c");
        device.setEnabled(true);
        return device;
    }

    private String maskCommunity(String community) {
        if (community == null || community.isBlank()) {
            return "(default/empty)";
        }
        if (community.length() <= 2) {
            return "**";
        }
        return community.substring(0, 1) + "***" + community.substring(community.length() - 1);
    }
}
