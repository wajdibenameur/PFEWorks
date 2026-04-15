package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.SourceAvailabilityDTO;

@Service
@RequiredArgsConstructor
public class MonitoringWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(MonitoringWebSocketPublisher.class);

    public static final String TOPIC_SOURCES = "/topic/monitoring/sources";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishSourceAvailability(SourceAvailabilityDTO event) {
        log.debug("Publishing source availability update for {} to {}", event.getSource(), TOPIC_SOURCES);
        messagingTemplate.convertAndSend(TOPIC_SOURCES, event);
    }
}
