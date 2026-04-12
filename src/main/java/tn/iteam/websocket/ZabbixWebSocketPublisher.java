package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.domain.ZabbixMetric;
import tn.iteam.dto.ZabbixProblemDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZabbixWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(ZabbixWebSocketPublisher.class);

    public static final String TOPIC_PROBLEMS = "/topic/zabbix/problems";
    public static final String TOPIC_METRICS = "/topic/zabbix/metrics";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishProblems(List<ZabbixProblemDTO> problems) {
        log.debug("Publishing {} Zabbix problems to WebSocket topic {}", problems.size(), TOPIC_PROBLEMS);
        messagingTemplate.convertAndSend(TOPIC_PROBLEMS, problems);
    }

    public void publishMetrics(List<ZabbixMetric> metrics) {
        log.debug("Publishing {} Zabbix metrics to WebSocket topic {}", metrics.size(), TOPIC_METRICS);
        messagingTemplate.convertAndSend(TOPIC_METRICS, metrics);
    }
}
