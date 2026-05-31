package tn.iteam.scheduler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodic Observium refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObserviumScheduler {

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${observium.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedDelayString = "${observium.scheduler.problems.rate:60000}",
            initialDelayString = "${observium.scheduler.problems.initial-delay:45000}"
    )
    public void refreshProblemsAndMetrics() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Observium monitoring JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.OBSERVIUM.name(), retryBackoffMs)) {
            log.debug("Skipping Observium scheduler refresh because retry cooldown is active.");
            running.set(false);
            return;
        }
        log.info("Observium monitoring JOB START");
        integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.OBSERVIUM)))
                .doFinally(signalType -> running.set(false))
                .subscribe(
                        unused -> log.info("Observium monitoring JOB DONE durationMs={}", System.currentTimeMillis() - startedAt),
                        throwable -> log.warn(
                                "Observium monitoring JOB FAILED: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
