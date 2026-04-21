package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.integration.IntegrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZkBioWebSocketPublisher;

/**
 * Periodic ZKBio refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ZkBioScheduler {

    @Qualifier("zkBioIntegrationService")
    private final IntegrationService zkBioIntegrationService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;
    private final ZkBioWebSocketPublisher zkBioWebSocketPublisher;

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.problems.rate:30000}",
            initialDelayString = "${zkbio.scheduler.problems.initial-delay:30000}"
    )
    public void refreshProblemsAndMetrics() {
        zkBioIntegrationService.refreshProblems();
        zkBioIntegrationService.refreshMetrics();
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZKBIO);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZKBIO);
    }

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.devices.rate:60000}",
            initialDelayString = "${zkbio.scheduler.devices.initial-delay:60000}"
    )
    public void refreshAttendanceDevicesAndStatus() {
        zkBioIntegrationService.refreshAttendance();
        zkBioWebSocketPublisher.publishAttendanceFromSnapshot();
        zkBioWebSocketPublisher.publishDevicesFromSnapshot();
        zkBioWebSocketPublisher.publishStatusFromSnapshot();
    }
}
