package tn.iteam.scheduler;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

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
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    @Scheduled(
            fixedRateString = "${observium.scheduler.problems.rate:60000}",
            initialDelayString = "${observium.scheduler.problems.initial-delay:45000}"
    )
    public void refreshProblemsAndMetrics() {
        integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.OBSERVIUM)))
                .subscribe(
                        unused -> {
                        },
                        throwable -> log.warn("Observium scheduler failed but application remains available: {}", throwable.getMessage())
                );
    }
}
