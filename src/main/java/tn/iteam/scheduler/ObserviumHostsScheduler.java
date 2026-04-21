package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.integration.ObserviumIntegrationService;

/**
 * Periodic Observium host inventory refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
@RequiredArgsConstructor
public class ObserviumHostsScheduler {

    private final ObserviumIntegrationService observiumIntegrationService;

    @Scheduled(
            fixedRateString = "${observium.scheduler.hosts.rate:120000}",
            initialDelayString = "${observium.scheduler.hosts.initial-delay:60000}"
    )
    public void refreshHosts() {
        observiumIntegrationService.refreshHosts();
    }
}
