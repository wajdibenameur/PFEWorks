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

import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean monitoringJobRunning = new AtomicBoolean(false);
    private final AtomicBoolean attendanceJobRunning = new AtomicBoolean(false);

    @Value("${zkbio.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedDelayString = "${zkbio.scheduler.problems.rate:30000}",
            initialDelayString = "${zkbio.scheduler.problems.initial-delay:30000}"
    )
    public void refreshProblemsAndMetrics() {
        if (!monitoringJobRunning.compareAndSet(false, true)) {
            log.warn("ZKBio monitoring JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), retryBackoffMs)) {
            log.debug("Skipping ZKBio monitoring scheduler refresh because retry cooldown is active.");
            monitoringJobRunning.set(false);
            return;
        }
        log.info("ZKBio monitoring JOB START");
        zkBioIntegrationService.refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZKBIO)))
                .doFinally(signalType -> monitoringJobRunning.set(false))
                .subscribe(
                        unused -> log.info("ZKBio monitoring JOB DONE durationMs={}", System.currentTimeMillis() - startedAt),
                        throwable -> log.warn(
                                "ZKBio monitoring JOB FAILED: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }

    @Scheduled(
            fixedDelayString = "${zkbio.scheduler.devices.rate:60000}",
            initialDelayString = "${zkbio.scheduler.devices.initial-delay:60000}"
    )
    public void refreshAttendanceDevicesAndStatus() {
        if (!attendanceJobRunning.compareAndSet(false, true)) {
            log.warn("ZKBio attendance JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZKBIO.name(), retryBackoffMs)) {
            log.debug("Skipping ZKBio attendance scheduler refresh because retry cooldown is active.");
            attendanceJobRunning.set(false);
            return;
        }
        log.info("ZKBio attendance JOB START");
        zkBioIntegrationService.refreshAttendanceAsync()
                .then(Mono.fromRunnable(snapshotPublicationService::publishZkBioSnapshots))
                .doFinally(signalType -> attendanceJobRunning.set(false))
                .subscribe(
                        unused -> log.info("ZKBio attendance JOB DONE durationMs={}", System.currentTimeMillis() - startedAt),
                        throwable -> log.warn(
                                "ZKBio attendance JOB FAILED: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
