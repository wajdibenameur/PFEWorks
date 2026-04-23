package tn.iteam;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.ZkBioIntegrationOperations;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.service.support.MonitoringSnapshotPublicationService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single bootstrap entry point for monitoring warmup.
 *
 * Startup is responsible only for the initial snapshot hydration and the first
 * websocket publication after the Spring context is ready.
 *
 * Periodic refreshes remain owned by the source-specific schedulers.
 */
@Slf4j
@Component
public class MonitoringStartup {
    private final AtomicBoolean warmupStarted = new AtomicBoolean(false);

    private final IntegrationServiceRegistry integrationServiceRegistry;
    private final ZkBioIntegrationOperations zkBioIntegrationOperations;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    public MonitoringStartup(
            IntegrationServiceRegistry integrationServiceRegistry,
            ZkBioIntegrationOperations zkBioIntegrationOperations,
            MonitoringSnapshotPublicationService snapshotPublicationService
    ) {
        this.integrationServiceRegistry = integrationServiceRegistry;
        this.zkBioIntegrationOperations = zkBioIntegrationOperations;
        this.snapshotPublicationService = snapshotPublicationService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupInitialSnapshots() {
        if (!warmupStarted.compareAndSet(false, true)) {
            log.debug("Monitoring warmup already started; skipping duplicate trigger.");
            return;
        }

        log.info("Application is ready; starting monitoring warmup in the background.");

        refreshSafely("Zabbix", integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)
                .refreshAsync()
                .doOnSuccess(unused -> snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZABBIX)));
        refreshSafely("Observium", integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM)
                .refreshAsync()
                .doOnSuccess(unused -> snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.OBSERVIUM)));
        refreshSafely("ZKBio", zkBioIntegrationOperations.refreshAsync()
                .then(zkBioIntegrationOperations.refreshAttendanceAsync())
                .doOnSuccess(unused -> {
                    snapshotPublicationService.publishMonitoringSnapshots(MonitoringSourceType.ZKBIO);
                    snapshotPublicationService.publishZkBioSnapshots();
                }));
        refreshSafely("Camera", integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refreshAsync());

        log.info("Monitoring warmup completed.");
    }

    private void refreshSafely(String source, reactor.core.publisher.Mono<Void> action) {
        action.subscribe(
                unused -> {
                },
                throwable -> log.warn("Startup refresh for {} failed: {}", source, throwable.getMessage())
        );
    }
}
