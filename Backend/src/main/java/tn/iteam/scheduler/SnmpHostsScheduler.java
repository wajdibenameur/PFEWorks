package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.config.SnmpProperties;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.SourceAvailabilityService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodic SNMP device inventory refresh scheduler.
 *
 * Initial warmup is handled by {@code MonitoringStartup}; this component is
 * limited to recurring refresh cycles after application startup.
 */
@Component
public class SnmpHostsScheduler {

    private static final Logger log = LoggerFactory.getLogger(SnmpHostsScheduler.class);

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final SnmpProperties snmpProperties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public SnmpHostsScheduler(
            IntegrationServiceRegistry integrationServiceRegistry,
            SourceAvailabilityService sourceAvailabilityService,
            SnmpProperties snmpProperties
    ) {
        this.integrationServiceRegistry = integrationServiceRegistry;
        this.sourceAvailabilityService = sourceAvailabilityService;
        this.snmpProperties = snmpProperties;
    }

    public SnmpHostsScheduler(
            IntegrationServiceRegistry integrationServiceRegistry,
            SourceAvailabilityService sourceAvailabilityService
    ) {
        this(integrationServiceRegistry, sourceAvailabilityService, new SnmpProperties());
    }

    @Value("${snmp.scheduler.retry-backoff-ms:60000}")
    private long retryBackoffMs = 60000L;

    @Scheduled(
            fixedDelayString = "${snmp.scheduler.devices.rate:${snmp.polling.interval:60000}}",
            initialDelayString = "${snmp.scheduler.devices.initial-delay:60000}"
    )
    public void refreshHosts() {
        if (!snmpProperties.isEnabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("SNMP devices JOB SKIPPED already running");
            return;
        }
        long startedAt = System.currentTimeMillis();
        if (!sourceAvailabilityService.shouldAttempt(MonitoringSourceType.SNMP.name(), retryBackoffMs)) {
            log.debug("Skipping SNMP devices scheduler refresh because retry cooldown is active.");
            running.set(false);
            return;
        }
        try {
            log.info("SNMP devices JOB START");
            integrationServiceRegistry.getRequired(MonitoringSourceType.SNMP).refreshHosts();
            log.info("SNMP devices JOB DONE durationMs={}", System.currentTimeMillis() - startedAt);
        } catch (Exception exception) {
            log.warn("SNMP devices JOB FAILED: {}", exception.getMessage());
        } finally {
            running.set(false);
        }
    }
}
