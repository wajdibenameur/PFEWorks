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

    public void publishSourceAvailability(SourceAvailabilityDTO event) {
        log.debug("Publishing source availability update for {} to {}", event.getSource(), TOPIC_SOURCES);
        messagingTemplate.convertAndSend(TOPIC_SOURCES, event);
    }

    public void publishProblems(List<UnifiedMonitoringProblemDTO> problems) {
        List<UnifiedMonitoringProblemDTO> payload = problems == null ? List.of() : problems;
        log.debug("Publishing {} monitoring problems", payload.size());
        messagingTemplate.convertAndSend(TOPIC_PROBLEMS, payload);
    }

    public void publishMetrics(List<UnifiedMonitoringMetricDTO> metrics) {
        List<UnifiedMonitoringMetricDTO> payload = metrics == null ? List.of() : metrics;
        log.debug("Publishing {} monitoring metrics", payload.size());
        messagingTemplate.convertAndSend(TOPIC_METRICS, payload);
    }
}
