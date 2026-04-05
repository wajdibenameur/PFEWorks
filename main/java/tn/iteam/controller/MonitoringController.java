package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.service.MonitoringService;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;

    @PostMapping("/collect")
    public ResponseEntity<String> collectAll() {
        monitoringService.collectAll();
        return ResponseEntity.ok("ALL SERVICES COLLECTED");
    }

    @PostMapping("/collect/zabbix")
    public ResponseEntity<String> collectZabbix() {
        monitoringService.collectZabbix();
        return ResponseEntity.ok("ZABBIX COLLECTED");
    }

    @PostMapping("/collect/observium")
    public ResponseEntity<String> collectObservium() {
        monitoringService.collectObservium();
        return ResponseEntity.ok("OBSERVIUM COLLECTED");
    }

    @PostMapping("/collect/camera")
    public ResponseEntity<String> collectCamera() {
        monitoringService.collectCamera();
        return ResponseEntity.ok("CAMERA COLLECTED");
    }
}
