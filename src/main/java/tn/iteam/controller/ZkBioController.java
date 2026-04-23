package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import tn.iteam.domain.ApiResponse;
import tn.iteam.dto.ServiceStatusDTO;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.service.ZkBioServiceInterface;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/zkbio")
@RequiredArgsConstructor
public class ZkBioController {

    private final ZkBioServiceInterface zkBioService;
    private final ZkBioIntegrationOperations zkBioIntegrationService;

    @GetMapping("/status")
    public ServiceStatusDTO getServerStatus() {
        log.info("GET /api/zkbio/status");
        return zkBioService.getServerStatus();
    }

    @GetMapping("/devices")
    public List<ServiceStatusDTO> getDevices() {
        log.info("GET /api/zkbio/devices");
        return zkBioService.fetchDevices();
    }

    @GetMapping("/problems")
    public List<ZkBioProblemDTO> getProblems() {
        log.info("GET /api/zkbio/problems");
        return zkBioService.fetchProblems();
    }

    @GetMapping("/attendance")
    public List<ZkBioAttendanceDTO> getAttendanceLogs() {
        log.info("GET /api/zkbio/attendance");
        return zkBioService.fetchAttendanceLogs();
    }

    @GetMapping("/attendance/range")
    public List<ZkBioAttendanceDTO> getAttendanceLogsByRange(
            @RequestParam long startTime,
            @RequestParam long endTime) {
        log.info("GET /api/zkbio/attendance/range?startTime={}&endTime={}", startTime, endTime);
        return zkBioService.fetchAttendanceLogs(startTime, endTime);
    }

    @PostMapping("/collect")
    public Mono<ResponseEntity<ApiResponse<Void>>> triggerCollection() {
        log.info("POST /api/zkbio/collect");
        return zkBioIntegrationService.refreshAllAndPublishAsync()
                .thenReturn(ResponseEntity.ok(
                        ApiResponse.<Void>builder()
                                .success(true)
                                .message("ZKBIO COLLECTED")
                                .source("SYSTEM")
                                .build()
                ));
    }

    @GetMapping("/users")
    public List<ZkBioAttendanceDTO> getUsers() {
        log.info("GET /api/zkbio/users");
        return zkBioService.fetchUsers();
    }
}
