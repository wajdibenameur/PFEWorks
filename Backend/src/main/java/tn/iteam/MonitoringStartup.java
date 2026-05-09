package tn.iteam;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Component;
import tn.iteam.integration.IntegrationServiceRegistry;
import tn.iteam.integration.ZkBioRefreshOrchestrationService;
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
    private final ZkBioRefreshOrchestrationService zkBioRefreshOrchestrationService;
    private final MonitoringSnapshotPublicationService snapshotPublicationService;

    public MonitoringStartup(
            IntegrationServiceRegistry integrationServiceRegistry,
            ZkBioRefreshOrchestrationService zkBioRefreshOrchestrationService,
            MonitoringSnapshotPublicationService snapshotPublicationService
    ) {
        this.integrationServiceRegistry = integrationServiceRegistry;
        this.zkBioRefreshOrchestrationService = zkBioRefreshOrchestrationService;
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

        refreshSafely("monitoring warmup", reactor.core.publisher.Mono.whenDelayError(
                integrationServiceRegistry.getRequired(MonitoringSourceType.ZABBIX)
                        .refreshAsync(),
                integrationServiceRegistry.getRequired(MonitoringSourceType.OBSERVIUM)
                        .refreshAsync(),
                zkBioRefreshOrchestrationService.refreshMonitoringAndAttendanceAsync(),
                integrationServiceRegistry.getRequired(MonitoringSourceType.CAMERA).refreshAsync()
        ).then(reactor.core.publisher.Mono.fromRunnable(() -> {
            snapshotPublicationService.publishMonitoringSnapshots(java.util.List.of(
                    MonitoringSourceType.ZABBIX,
                    MonitoringSourceType.OBSERVIUM,
                    MonitoringSourceType.ZKBIO
            ));
            snapshotPublicationService.publishZkBioSnapshots();
            log.info("Monitoring warmup completed.");
        })));
    }

    private void refreshSafely(String source, reactor.core.publisher.Mono<Void> action) {
        action.subscribe(
                unused -> {
                },
                throwable -> log.warn(
                        "Startup refresh for {} failed but application remains available: {}",
                        source,
                        safeMessage(throwable)
                )
        );
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown cause";
        }
        return throwable.getMessage();
    }
}
