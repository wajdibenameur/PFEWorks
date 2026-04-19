package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.service.ZkBioMonitoringService;
import tn.iteam.service.ZkBioServiceInterface;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZkBioScheduler {

    private final ZkBioServiceInterface zkBioService;
    private final ZkBioMonitoringService zkBioMonitoringService;
    private final ZkBioWebSocketPublisher publisher;
    private final MonitoringWebSocketPublisher monitoringPublisher;

    @Scheduled(fixedRateString = "${zkbio.scheduler.problems.rate:30000}")
    public void fetchAndPublishProblems() {
        log.info("Scheduled: Fetching ZKBio problems for WebSocket broadcast");
        try {
            List<ZkBioProblemDTO> problems = zkBioService.fetchProblems();
            publisher.publishProblems(problems);
            List<UnifiedMonitoringProblemDTO> monitoringProblems = zkBioMonitoringService.fetchMonitoringProblems();
            monitoringPublisher.publishProblems(monitoringProblems);
            log.info("Published {} ZKBio problems to WebSocket", problems.size());
        } catch (Exception e) {
            log.error("Error fetching/publishing ZKBio problems", e);
        }
    }

    @Scheduled(fixedRateString = "${zkbio.scheduler.devices.rate:60000}")
    public void fetchAndPublishAttendance() {
        log.info("Scheduled: Fetching ZKBio attendance logs for WebSocket broadcast");
        try {
            publisher.publishStatus(zkBioService.getServerStatus());
            publisher.publishDevices(zkBioService.fetchDevices());
            List<ZkBioAttendanceDTO> logs = zkBioService.fetchAttendanceLogs();
            publisher.publishAttendance(logs);
            log.info("Published {} ZKBio attendance logs to WebSocket", logs.size());
        } catch (Exception e) {
            log.error("Error fetching/publishing ZKBio attendance logs", e);
        }
    }
}
