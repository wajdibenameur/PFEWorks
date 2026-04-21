package tn.iteam.scheduler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.integration.ObserviumIntegrationService;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.websocket.MonitoringWebSocketPublisher;

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

    private final ObserviumIntegrationService observiumIntegrationService;
    private final MonitoringWebSocketPublisher monitoringWebSocketPublisher;

    @Scheduled(
            fixedRateString = "${observium.scheduler.problems.rate:60000}",
            initialDelayString = "${observium.scheduler.problems.initial-delay:45000}"
    )
    public void refreshProblemsAndMetrics() {
        observiumIntegrationService.refreshProblems();
        observiumIntegrationService.refreshMetrics();
        monitoringWebSocketPublisher.publishProblemsFromSnapshot(MonitoringSourceType.OBSERVIUM);
        monitoringWebSocketPublisher.publishMetricsFromSnapshot(MonitoringSourceType.OBSERVIUM);
    }
}
