package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.service.MonitoringAggregationService;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixLiveSynchronizationService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.websocket.MonitoringWebSocketPublisher;
import tn.iteam.websocket.ZabbixWebSocketPublisher;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ZabbixScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZabbixScheduler.class);

    private static final String SOURCE_ZABBIX = "ZABBIX";

    private static final String LOG_PROBLEMS_UNAVAILABLE =
            "Zabbix unavailable while refreshing problems: {}";
    private static final String LOG_METRICS_UNAVAILABLE =
            "Zabbix unavailable while refreshing metrics: {}";

    private static final String LOG_SKIP_PROBLEMS =
            "Skipping Zabbix problems WebSocket publish because no persisted fallback is available";
    private static final String LOG_SKIP_METRICS =
            "Skipping Zabbix metrics WebSocket publish because no persisted fallback is available";

    private final ZabbixProblemService problemService;
    private final ZabbixMetricsService metricsService;
    private final ZabbixLiveSynchronizationService liveSynchronizationService;
    private final ZabbixWebSocketPublisher publisher;
    private final MonitoringWebSocketPublisher monitoringPublisher;
    private final MonitoringAggregationService monitoringAggregationService;
    private final SourceAvailabilityService availabilityService;

    @Value("${zabbix.retry-backoff-ms:120000}")
    private long retryBackoffMs;

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.problems.rate:30000}",
            initialDelayString = "${zabbix.scheduler.problems.initial-delay:30000}"
    )
    public void fetchAndPublishProblems() {
        log.info("Scheduled: Fetching Zabbix problems for WebSocket broadcast");

        try {
            liveSynchronizationService.synchronizeForProblemsTick();
        } catch (IntegrationTimeoutException | IntegrationUnavailableException e) {
            log.warn(LOG_PROBLEMS_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while refreshing Zabbix problems", e);
            return;
        }

        try {
            List<ZabbixProblemDTO> problems = problemService.getPersistedFilteredActiveProblems();

            if (!availabilityService.isAvailable(SOURCE_ZABBIX)) {
                String lastError = availabilityService.getLastError(SOURCE_ZABBIX);
                if (availabilityService.isRetryCooldownActive(SOURCE_ZABBIX, retryBackoffMs)) {
                    log.debug("Zabbix remains unavailable during retry backoff while refreshing problems: {}", lastError);
                } else {
                    log.warn(LOG_PROBLEMS_UNAVAILABLE, lastError);
                }

                if (problems.isEmpty()) {
                    log.warn(LOG_SKIP_PROBLEMS);
                    return;
                }

                availabilityService.markDegraded(
                        SOURCE_ZABBIX,
                        lastError != null && !lastError.isBlank()
                                ? lastError
                                : "Live Zabbix problems unavailable, publishing last persisted snapshot"
                );

                if (availabilityService.isRetryCooldownActive(SOURCE_ZABBIX, retryBackoffMs)) {
                    log.debug("Publishing last persisted Zabbix problems snapshot ({} problems)", problems.size());
                } else {
                    log.warn("Publishing last persisted Zabbix problems snapshot ({} problems)", problems.size());
                }
            }

            publisher.publishProblems(problems);
            List<UnifiedMonitoringProblemDTO> monitoringProblems =
                    monitoringAggregationService.getProblems(MonitoringSourceType.ZABBIX).getData();
            monitoringPublisher.publishProblems(monitoringProblems);
            log.info("Published {} Zabbix problems to WebSocket", problems.size());
        } catch (Exception e) {
            log.error("Unexpected error while publishing Zabbix problems", e);
        }
    }

    @Scheduled(
            fixedRateString = "${zabbix.scheduler.metrics.rate:60000}",
            initialDelayString = "${zabbix.scheduler.metrics.initial-delay:60000}"
    )
    public void fetchAndPublishMetrics() {
        log.info("Scheduled: Fetching Zabbix metrics for WebSocket broadcast");

        try {
            liveSynchronizationService.synchronizeForMetricsTick();
        } catch (IntegrationTimeoutException | IntegrationUnavailableException e) {
            log.warn(LOG_METRICS_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while refreshing Zabbix metrics", e);
            return;
        }

        try {
            List<ZabbixMetric> metrics = metricsService.getPersistedMetricsSnapshot();

            if (!availabilityService.isAvailable(SOURCE_ZABBIX)) {
                String lastError = availabilityService.getLastError(SOURCE_ZABBIX);
                if (availabilityService.isRetryCooldownActive(SOURCE_ZABBIX, retryBackoffMs)) {
                    log.debug("Zabbix remains unavailable during retry backoff while refreshing metrics: {}", lastError);
                } else {
                    log.warn(LOG_METRICS_UNAVAILABLE, lastError);
                }

                if (metrics.isEmpty()) {
                    log.warn(LOG_SKIP_METRICS);
                    return;
                }

                availabilityService.markDegraded(
                        SOURCE_ZABBIX,
                        lastError != null && !lastError.isBlank()
                                ? lastError
                                : "Live Zabbix metrics unavailable, publishing last persisted snapshot"
                );

                if (availabilityService.isRetryCooldownActive(SOURCE_ZABBIX, retryBackoffMs)) {
                    log.debug("Publishing last persisted Zabbix metrics snapshot ({} metrics)", metrics.size());
                } else {
                    log.warn("Publishing last persisted Zabbix metrics snapshot ({} metrics)", metrics.size());
                }
            }

            publisher.publishMetrics(metrics);
            List<UnifiedMonitoringMetricDTO> monitoringMetrics =
                    monitoringAggregationService.getMetrics(MonitoringSourceType.ZABBIX).getData();
            monitoringPublisher.publishMetrics(monitoringMetrics);
            log.info("Published {} Zabbix metrics to WebSocket", metrics.size());
        } catch (Exception e) {
            log.error("Unexpected error while publishing Zabbix metrics", e);
        }
    }
}
