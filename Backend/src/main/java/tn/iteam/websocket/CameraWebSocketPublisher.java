package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.CameraDeviceDTO;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CameraWebSocketPublisher {

    private static final Logger log = LoggerFactory.getLogger(CameraWebSocketPublisher.class);
    public static final String TOPIC_CAMERA = "/topic/camera";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishStatusChanges(List<CameraDeviceDTO> changedDevices) {
        if (changedDevices == null || changedDevices.isEmpty()) {
            return;
        }
        try {
            log.debug("Publishing {} camera status changes to {}", changedDevices.size(), TOPIC_CAMERA);
            messagingTemplate.convertAndSend(TOPIC_CAMERA, changedDevices);
        } catch (Exception exception) {
            log.warn("Camera websocket publish failed but polling will continue: {}", exception.getMessage());
        }
    }
}
