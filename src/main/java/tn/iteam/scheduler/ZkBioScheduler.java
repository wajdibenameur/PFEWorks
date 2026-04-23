package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

/**
 * Periodic ZKBio refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ZkBioScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZkBioScheduler.class);

    private final ZkBioIntegrationOperations zkBioIntegrationService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.problems.rate:30000}",
            initialDelayString = "${zkbio.scheduler.problems.initial-delay:30000}"
    )
    public void refreshProblemsAndMetrics() {
        zkBioIntegrationService.refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZKBIO)))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn("ZKBio monitoring scheduler failed but application remains available: {}", throwable.getMessage())
                );
    }

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.devices.rate:60000}",
            initialDelayString = "${zkbio.scheduler.devices.initial-delay:60000}"
    )
    public void refreshAttendanceDevicesAndStatus() {
        zkBioIntegrationService.refreshAttendanceAsync()
                .then(Mono.fromRunnable(snapshotPublicationService::publishZkBioSnapshots))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn("ZKBio attendance scheduler failed but application remains available: {}", throwable.getMessage())
                );
    }
}
