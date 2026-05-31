package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.SourceAvailabilityService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodic Observium host inventory refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ObserviumHostsScheduler {

    private static final Logger log = LoggerFactory.getLogger(ObserviumHostsScheduler.class);

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${observium.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedDelayString = "${observium.scheduler.hosts.rate:120000}",
            initialDelayString = "${observium.scheduler.hosts.initial-delay:60000}"
    )
    public void refreshHosts() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Observium hosts JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.OBSERVIUM.name(), retryBackoffMs)) {
            log.debug("Skipping Observium hosts scheduler refresh because retry cooldown is active.");
            running.set(false);
            return;
        }
        try {
            log.info("Observium hosts JOB START");
            integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshHosts();
            log.info("Observium hosts JOB DONE durationMs={}", System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            log.warn("Observium hosts JOB FAILED: {}", exception.getMessage());
        } finally {
            running.set(false);
        }
    }
}
