package tn.iteam.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.config.SnmpProperties;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodic SNMP refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Slf4j
@Component
public class SnmpScheduler {

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;
    private final SnmpProperties snmpProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public SnmpScheduler(
            IntegrationServiceRegistry integrationServiceRegistry,
            SourceAvailabilityService sourceAvailabilityService,
            MonitoringSnapshotPublicationService snapshotPublicationService,
            SnmpProperties snmpProperties
    ) {
        this.integrationServiceRegistry = integrationServiceRegistry;
        this.sourceAvailabilityService = sourceAvailabilityService;
        this.snapshotPublicationService = snapshotPublicationService;
        this.snmpProperties = snmpProperties;
    }

    public SnmpScheduler(
            IntegrationServiceRegistry integrationServiceRegistry,
            SourceAvailabilityService sourceAvailabilityService,
            MonitoringSnapshotPublicationService snapshotPublicationService
    ) {
        this(integrationServiceRegistry, sourceAvailabilityService, snapshotPublicationService, new SnmpProperties());
    }

    @Value("${snmp.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedDelayString = "${snmp.scheduler.problems.rate:${snmp.polling.interval:60000}}",
            initialDelayString = "${snmp.scheduler.problems.initial-delay:45000}"
    )
    public void refreshProblemsAndMetrics() {
        if (!snmpProperties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("SNMP monitoring JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.SNMP.name(), retryBackoffMs)) {
            log.debug("Skipping SNMP scheduler refresh because retry cooldown is active.");
            running.set(false);
            return;
        }
        log.info("SNMP monitoring JOB START");
        integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP).refreshAsync()
                .then(Mono.fromRunnable(() ->
                        snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.SNMP)))
                .doFinally(signalType -> running.set(false))
                .subscribe(
                        unused -> log.info("SNMP monitoring JOB DONE durationMs={}", System.currentTimeMillis() - startedAt),
                        throwable -> log.warn(
                                "SNMP monitoring JOB FAILED: {}",
                                throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "unknown cause"
                        )
                );
    }
}
