package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tn.iteam.integration.CameraIntegrationService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.util.MonitoringConstants;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class CameraPollingScheduler {

    private final CameraIntegrationService cameraIntegrationService;
    private final SourceAvailabilityService sourceAvailabilityService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    @Value("${camera.scheduler.retry-backoff-ms:300000}")
    private long retryBackoffMs = 300000L;
    @Value("${camera.scheduler.refresh-timeout-ms:45000}")
    private long refreshTimeoutMs = 45000L;

    @Scheduled(
            fixedDelayString = "${camera.poll-interval-ms:30000}",
            initialDelayString = "${camera.poll-interval-ms:30000}"
    )
    public void refreshCameraHealth() {
        if (!sourceAvailabilityService.shouldAttempt(MonitoringConstants.SOURCE_CAMERA, retryBackoffMs)) {
            log.debug("Skipping camera scheduler refresh because retry cooldown is active.");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.debug("Camera polling skipped: previous run still active");
            return;
        }
        cameraIntegrationService.refreshAsync()
                .timeout(Duration.ofMillis(refreshTimeoutMs))
                .onErrorResume(exception -> {
                    running.set(false);
                    if (exception instanceof TimeoutException) {
                        log.warn(
                                "Camera polling timed out after {} ms. Source stays DEGRADED and the next cycle will continue.",
                                refreshTimeoutMs
                        );
                    } else {
                        log.warn("Camera polling scheduler execution failed: {}", exception.getMessage());
                    }
                    sourceAvailabilityService.markDegraded(
                            MonitoringConstants.SOURCE_CAMERA,
                            "Camera polling scheduler failure: " + (exception.getMessage() == null ? "unknown" : exception.getMessage())
                    );
                    return Mono.empty();
                })
                .doFinally(signalType -> running.set(false))
                .subscribe();
    }
}
