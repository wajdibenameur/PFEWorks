package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.SourceAvailabilityService;
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
    private final SourceAvailabilityService sourceAvailabilityService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    @Value("${zkbio.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.problems.rate:30000}",
            initialDelayString = "${zkbio.scheduler.problems.initial-delay:30000}"
    )
    public void refreshProblemsAndMetrics() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), retryBackoffMs)) {
            log.debug("Skipping ZKBio monitoring scheduler refresh because retry cooldown is active.");
            return;
        }
        zkBioIntegrationService.refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZKBIO)))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn(
                                "ZKBio monitoring scheduler failed but application remains available: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }

    @Scheduled(
            fixedRateString = "${zkbio.scheduler.devices.rate:60000}",
            initialDelayString = "${zkbio.scheduler.devices.initial-delay:60000}"
    )
    public void refreshAttendanceDevicesAndStatus() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), retryBackoffMs)) {
            log.debug("Skipping ZKBio attendance scheduler refresh because retry cooldown is active.");
            return;
        }
        zkBioIntegrationService.refreshAttendanceAsync()
                .then(Mono.fromRunnable(snapshotPublicationService::publishZkBioSnapshots))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn(
                                "ZKBio attendance scheduler failed but application remains available: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
