package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.integration.ZabbixIntegrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

/**
 * Periodic Zabbix refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ZabbixScheduler {

    private final ZabbixIntegrationService zabbixIntegrationService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.problems.rate:30000}",
            initialDelayString = "${zabbix.scheduler.problems.initial-delay:30000}"
    )
    public void fetchAndPublishProblems() {
        zabbixIntegrationService.refreshProblems();
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.ZABBIX);
    }

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.metrics.rate:60000}",
            initialDelayString = "${zabbix.scheduler.metrics.initial-delay:60000}"
    )
    public void fetchAndPublishMetrics() {
        zabbixIntegrationService.refreshMetrics();
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.ZABBIX);
    }
}
