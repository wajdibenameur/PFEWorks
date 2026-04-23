package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
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
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.problems.rate:30000}",
            initialDelayString = "${zabbix.scheduler.problems.initial-delay:30000}"
    )
    public void fetchAndPublishProblems() {
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshProblems();
        snapshotPublicationService.publishProblemsSnapshot(MonitoringSourceType.ZABBIX);
    }

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.metrics.rate:60000}",
            initialDelayString = "${zabbix.scheduler.metrics.initial-delay:60000}"
    )
    public void fetchAndPublishMetrics() {
        integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX).refreshMetricsAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMetricsSnapshot(MonitoringSourceType.ZABBIX)))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn("Zabbix metrics scheduler failed but application remains available: {}", throwable.getMessage())
                );
    }
}
