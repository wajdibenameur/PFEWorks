package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.exception.IntegrationTimeoutException;
import tn.iteam.exception.IntegrationUnavailableException;
import tn.iteam.service.SourceAvailabilityService;
import tn.iteam.service.ZabbixLiveSynchronizationService;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
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
    private final SourceAvailabilityService availabilityService;

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
                log.warn(LOG_PROBLEMS_UNAVAILABLE, lastError);

                if (problems.isEmpty()) {
                    log.warn(LOG_SKIP_PROBLEMS);
                    return;
                }

                log.warn("Publishing last persisted Zabbix problems snapshot ({} problems)", problems.size());
            }

            publisher.publishProblems(problems);
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
                log.warn(LOG_METRICS_UNAVAILABLE, lastError);

                if (metrics.isEmpty()) {
                    log.warn(LOG_SKIP_METRICS);
                    return;
                }

                log.warn("Publishing last persisted Zabbix metrics snapshot ({} metrics)", metrics.size());
            }

            publisher.publishMetrics(metrics);
            log.info("Published {} Zabbix metrics to WebSocket", metrics.size());
        } catch (Exception e) {
            log.error("Unexpected error while publishing Zabbix metrics", e);
        }
    }
}