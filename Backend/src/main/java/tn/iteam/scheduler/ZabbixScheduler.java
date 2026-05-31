package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Periodic Zabbix refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ZabbixScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZabbixScheduler.class);

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;
    private final AtomicBoolean problemsJobRunning = new AtomicBoolean(false);
    private final AtomicBoolean metricsJobRunning = new AtomicBoolean(false);
    private volatile long lastMetricsJobStartedAtMs = 0L;

    @Value("${zabbix.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Value("${zabbix.scheduler.metrics.retry-backoff-ms:120000}")
    private long metricsRetryBackoffMs = 120000L;

    @Value("${zabbix.scheduler.metrics.min-gap-ms:120000}")
    private long metricsMinGapMs = 120000L;

    @Scheduled(
            fixedDelayString = "${zabbix.scheduler.problems.rate:30000}",
            initialDelayString = "${zabbix.scheduler.problems.initial-delay:30000}"
    )
    public void fetchAndPublishProblems() {
        if (!problemsJobRunning.compareAndSet(false, true)) {
            log.warn("Zabbix problems JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), retryBackoffMs)) {
            log.debug("Skipping Zabbix problems scheduler refresh because retry cooldown is active.");
            problemsJobRunning.set(false);
            return;
        }
        try {
            log.info("Zabbix problems JOB START");
            integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshProblems();
            snapshotPublicationService.publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
            log.info("Zabbix problems JOB DONE durationMs={}", System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            log.warn("Zabbix problems JOB FAILED: {}", exception.getMessage());
        } finally {
            problemsJobRunning.set(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${zabbix.scheduler.metrics.rate:60000}",
            initialDelayString = "${zabbix.scheduler.metrics.initial-delay:60000}"
    )
    public void fetchAndPublishMetrics() {
        long now = System.currentTimeMillis();
        long elapsedSinceLastStart = now - lastMetricsJobStartedAtMs;
        if (elapsedSinceLastStart < metricsMinGapMs) {
            log.debug(
                    "Zabbix metrics JOB SKIPPED min-gap active elapsedMs={} requiredMs={}",
                    elapsedSinceLastStart,
                    metricsMinGapMs
            );
            return;
        }
        if (!metricsJobRunning.compareAndSet(false, true)) {
            log.warn("Zabbix metrics JOB SKIPPED already running");
            return;
        }
        lastMetricsJobStartedAtMs = now;
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), metricsRetryBackoffMs)) {
            log.debug("Skipping Zabbix metrics scheduler refresh because retry cooldown is active.");
            metricsJobRunning.set(false);
            return;
        }
        log.info("Zabbix metrics JOB START");
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshMetricsAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMetricsSnapshot(MonitoringSourceType.ZABBIX)))
                .doFinally(signalType -> metricsJobRunning.set(false))
                .subscribe(
                        unused -> log.info("Zabbix metrics JOB DONE durationMs={}", System.currentTimeMillis() - startedAt),
                        throwable -> log.warn(
                                "Zabbix metrics JOB FAILED: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
