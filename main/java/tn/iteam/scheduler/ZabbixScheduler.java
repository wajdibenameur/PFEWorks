package tn.iteam.scheduler;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixProblemDTO;
import tn.iteam.service.ZabbixMetricsService;
import tn.iteam.service.ZabbixProblemService;
import tn.iteam.websocket.ZabbixWebSocketPublisher;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ZabbixScheduler {

    private static final Logger log = LoggerFactory.getLogger(ZabbixScheduler.class);

    private final ZabbixProblemService problemService;
    private final ZabbixMetricsService metricsService;
    private final ZabbixWebSocketPublisher publisher;

    @Scheduled(fixedRateString = "${zabbix.scheduler.problems.rate:30000}")
    public void fetchAndPublishProblems() {
        log.info("Scheduled: Fetching Zabbix problems for WebSocket broadcast");
        try {
            List<ZabbixProblemDTO> problems = problemService.fetchActiveProblems();
            publisher.publishProblems(problems);
            log.info("Published {} Zabbix problems to WebSocket", problems.size());
        } catch (Exception e) {
            log.error("Error fetching/publishing Zabbix problems", e);
        }
    }

    @Scheduled(fixedRateString = "${zabbix.scheduler.metrics.rate:60000}")
    public void fetchAndPublishMetrics() {
        log.info("Scheduled: Fetching Zabbix metrics for WebSocket broadcast");
        try {
            List<ZabbixMetric> metrics = metricsService.fetchMetrics();
            publisher.publishMetrics(metrics);
            log.info("Published {} Zabbix metrics to WebSocket", metrics.size());
        } catch (Exception e) {
            log.error("Error fetching/publishing Zabbix metrics", e);
        }
    }
}
