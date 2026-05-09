package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.SourceAvailabilityDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringMetricDTO;
import tn.iteam.monitoring.dto.UnifiedMonitoringProblemDTO;
import tn.iteam.monitoring.MonitoringSourceType;
import tn.iteam.monitoring.snapshot.SnapshotStore;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MonitoringWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitoringWebSocketPublisher.class);

    public static final String TOPIC_PROBLEMS = "/topic/monitoring/problems";
    public static final String TOPIC_METRICS = "/topic/monitoring/metrics";
    public static final String TOPIC_SOURCES = "/topic/monitoring/sources";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String DATASET_METRICS = "metrics";

    private final SimpMessagingTemplate messagingTemplate;
    private final SnapshotStore snapshotStore;

    public void publishSourceAvailability(SourceAvailabilityDTO event) {
        log.debug("Publishing source availability update for {} to {}", event.getSource(), TOPIC_SOURCES);
        messagingTemplate.convertAndSend(TOPIC_SOURCES, event);
    }

    public void publishProblemsFromSnapshot(MonitoringSourceType sourceType) {
        if (sourceType == null) {
            return;
        }

        snapshotStore.<List<UnifiedMonitoringProblemDTO>>get(DATASET_PROBLEMS, sourceType.name())
                .ifPresent(snapshot -> {
                    List<UnifiedMonitoringProblemDTO> problems = snapshot.data();
                    log.debug("Publishing {} monitoring problems from SnapshotStore for {}", problems.size(), sourceType);
                    messagingTemplate.convertAndSend(TOPIC_PROBLEMS, problems);
                });
    }

    public void publishMetricsFromSnapshot(MonitoringSourceType sourceType) {
        if (sourceType == null) {
            return;
        }

        snapshotStore.<List<UnifiedMonitoringMetricDTO>>get(DATASET_METRICS, sourceType.name())
                .ifPresent(snapshot -> {
                    List<UnifiedMonitoringMetricDTO> metrics = snapshot.data();
                    log.debug("Publishing {} monitoring metrics from SnapshotStore for {}", metrics.size(), sourceType);
                    messagingTemplate.convertAndSend(TOPIC_METRICS, metrics);
                });
    }
}
