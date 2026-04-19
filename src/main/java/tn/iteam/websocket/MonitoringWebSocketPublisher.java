package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoringWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitoringWebSocketPublisher.class);

    public static final String TOPIC_PROBLEMS = "/topic/monitoring/problems";
    public static final String TOPIC_METRICS = "/topic/monitoring/metrics";
    public static final String TOPIC_SOURCES = "/topic/monitoring/sources";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishProblems(List<UnifiedMonitoringProblemDTO> problems) {
        log.debug("Publishing {} monitoring problems to {}", problems.size(), TOPIC_PROBLEMS);
        messagingTemplate.convertAndSend(TOPIC_PROBLEMS, problems);
    }

    public void publishMetrics(List<UnifiedMonitoringMetricDTO> metrics) {
        log.debug("Publishing {} monitoring metrics to {}", metrics.size(), TOPIC_METRICS);
        messagingTemplate.convertAndSend(TOPIC_METRICS, metrics);
    }

    public void publishSourceAvailability(SourceAvailabilityDTO event) {
        log.debug("Publishing source availability update for {} to {}", event.getSource(), TOPIC_SOURCES);
        messagingTemplate.convertAndSend(TOPIC_SOURCES, event);
    }
}
