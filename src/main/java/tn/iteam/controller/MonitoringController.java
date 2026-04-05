package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.iteam.domain.ServiceStatus;
import tn.iteam.repository.ServiceStatusRepository;
import tn.iteam.service.MonitoringService;

import java.util.List;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final ServiceStatusRepository repository;

    // Endpoint global si besoin
    @PostMapping("/collect")
    public ResponseEntity<String> collectAll() {
        monitoringService.collectAll();
        return ResponseEntity.ok("ALL SERVICES COLLECTED");
    }

    // Endpoint spécifique Zabbix
    @PostMapping("/collect/zabbix")
    public ResponseEntity<String> collectZabbix() {
        monitoringService.collectZabbix();
        return ResponseEntity.ok("ZABBIX COLLECTED");
    }

    // Endpoint spécifique Observium
    @PostMapping("/collect/observium")
    public ResponseEntity<String> collectObservium() {
        monitoringService.collectObservium();
        return ResponseEntity.ok("OBSERVIUM COLLECTED");
    }

    // Endpoint spécifique Camera
    @PostMapping("/collect/camera")
    public ResponseEntity<String> collectCamera() {
        monitoringService.collectCamera();
        return ResponseEntity.ok("CAMERA COLLECTED");
    }

    // Pour voir tous les services en base
    @GetMapping
    public List<ServiceStatus> all() {
        return repository.findAll();
    }
}
