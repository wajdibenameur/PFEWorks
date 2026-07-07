package tn.iteam.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.domain.ApiResponse;
import tn.iteam.service.ZabbixCriticalHistorySyncResult;
import tn.iteam.service.ZabbixCriticalHistorySyncService;

@RestController
@RequestMapping("/api/admin/zabbix")
@RequiredArgsConstructor
@Tag(name = "Admin Zabbix", description = "API d'administration Zabbix")
public class AdminZabbixController {

    private final ZabbixCriticalHistorySyncService zabbixCriticalHistorySyncService;

    @PostMapping("/critical-history/sync")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    @Operation(summary = "Synchroniser l'historique critique Zabbix",
            description = "Lance manuellement une synchronisation indépendante des événements historiques Zabbix severity 4/5.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Synchronisation historique critique traitée"
    )
    public ResponseEntity<ApiResponse<ZabbixCriticalHistorySyncResult>> syncCriticalHistory() {
        ZabbixCriticalHistorySyncResult result = zabbixCriticalHistorySyncService.syncCriticalHistory();
        return ResponseEntity.ok(ApiResponse.<ZabbixCriticalHistorySyncResult>builder()
                .success(true)
                .message(result.message())
                .source("ZABBIX")
                .data(result)
                .build());
    }
}
