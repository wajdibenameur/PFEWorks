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

    @Value("${zabbix.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Value("${zabbix.scheduler.metrics.retry-backoff-ms:120000}")
    private long metricsRetryBackoffMs = 120000L;

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.problems.rate:30000}",
            initialDelayString = "${zabbix.scheduler.problems.initial-delay:30000}"
    )
    public void fetchAndPublishProblems() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), retryBackoffMs)) {
            log.debug("Skipping Zabbix problems scheduler refresh because retry cooldown is active.");
            return;
        }
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshProblems();
        snapshotPublicationService.publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
    }

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.metrics.rate:60000}",
            initialDelayString = "${zabbix.scheduler.metrics.initial-delay:60000}"
    )
    public void fetchAndPublishMetrics() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.ZABBIX.name(), metricsRetryBackoffMs)) {
            log.debug("Skipping Zabbix metrics scheduler refresh because retry cooldown is active.");
            return;
        }
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshMetricsAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMetricsSnapshot(MonitoringSourceType.ZABBIX)))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn(
                                "Zabbix metrics scheduler failed but application remains available: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
