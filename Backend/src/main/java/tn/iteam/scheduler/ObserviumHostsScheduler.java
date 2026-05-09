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

    @Value("${observium.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedRateString = "${observium.scheduler.hosts.rate:120000}",
            initialDelayString = "${observium.scheduler.hosts.initial-delay:60000}"
    )
    public void refreshHosts() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.OBSERVIUM.name(), retryBackoffMs)) {
            log.debug("Skipping Observium hosts scheduler refresh because retry cooldown is active.");
            return;
        }
        integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM).refreshHosts();
    }
}
