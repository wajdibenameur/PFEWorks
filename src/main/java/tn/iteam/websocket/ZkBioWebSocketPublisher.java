package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;

import java.util.List;

/**
 * Publisher WebSocket pour les données ZKBio
 * Publie vers les topics STOMP pour le frontend Angular
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    // ==================== TOPICS ====================
    public static final String TOPIC_PROBLEMS = "/topic/zkbio/problems";
    public static final String TOPIC_ATTENDANCE = "/topic/zkbio/attendance";
    public static final String TOPIC_DEVICES = "/topic/zkbio/devices";
    public static final String TOPIC_STATUS = "/topic/zkbio/status";

    // ==================== PUBLISH METHODS ====================

    /**
     * Publie les problèmes/alertes ZKBio
     */
    public void publishProblems(List<ZkBioProblemDTO> problems) {
        log.debug("Publishing {} ZKBio problems to WebSocket topic {}", problems.size(), TOPIC_PROBLEMS);
        messagingTemplate.convertAndSend(TOPIC_PROBLEMS, problems);
    }

    /**
     * Publie les journaux de présence
     */
    public void publishAttendance(List<ZkBioAttendanceDTO> logs) {
        log.debug("Publishing {} ZKBio attendance logs to WebSocket topic {}", logs.size(), TOPIC_ATTENDANCE);
        messagingTemplate.convertAndSend(TOPIC_ATTENDANCE, logs);
    }

    /**
     * Publie le statut des appareils
     */
    public void publishDevices(List<?> devices) {
        log.debug("Publishing ZKBio devices to WebSocket topic {}", TOPIC_DEVICES);
        messagingTemplate.convertAndSend(TOPIC_DEVICES, devices);
    }

    /**
     * Publie le statut du serveur ZKBio
     */
    public void publishStatus(Object status) {
        log.debug("Publishing ZKBio status to WebSocket topic {}", TOPIC_STATUS);
        messagingTemplate.convertAndSend(TOPIC_STATUS, status);
    }
}