package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.dto.ZkBioProblemDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;

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
    private final SnapshotStore snapshotStore;

    // ==================== TOPICS ====================
    public static final String TOPIC_PROBLEMS = "/topic/zkbio/problems";
    public static final String TOPIC_ATTENDANCE = "/topic/zkbio/attendance";
    public static final String TOPIC_DEVICES = "/topic/zkbio/devices";
    public static final String TOPIC_STATUS = "/topic/zkbio/status";
    private static final String DATASET_PROBLEMS = "problems";
    private static final String DATASET_ATTENDANCE = "attendance";
    private static final String DATASET_DEVICES = "devices";
    private static final String DATASET_STATUS = "status";

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

    public void publishProblemsFromSnapshot() {
        snapshotStore.<List<?>>get(DATASET_PROBLEMS, "ZKBIO")
                .ifPresent(snapshot -> {
                    List<?> problems = snapshot.data();
                    log.debug("Publishing {} ZKBio problems from SnapshotStore", problems.size());
                    messagingTemplate.convertAndSend(TOPIC_PROBLEMS, problems);
                });
    }

    public void publishAttendanceFromSnapshot() {
        snapshotStore.<List<ZkBioAttendanceDTO>>get(DATASET_ATTENDANCE, "ZKBIO")
                .ifPresent(snapshot -> {
                    List<ZkBioAttendanceDTO> logs = snapshot.data();
                    log.debug("Publishing {} ZKBio attendance logs from SnapshotStore", logs.size());
                    messagingTemplate.convertAndSend(TOPIC_ATTENDANCE, logs);
                });
    }

    public void publishDevicesFromSnapshot() {
        snapshotStore.<List<?>>get(DATASET_DEVICES, "ZKBIO")
                .ifPresent(snapshot -> {
                    List<?> devices = snapshot.data();
                    log.debug("Publishing {} ZKBio devices from SnapshotStore", devices.size());
                    messagingTemplate.convertAndSend(TOPIC_DEVICES, devices);
                });
    }

    public void publishStatusFromSnapshot() {
        snapshotStore.<Object>get(DATASET_STATUS, "ZKBIO")
                .ifPresent(snapshot -> {
                    log.debug("Publishing ZKBio status from SnapshotStore");
                    messagingTemplate.convertAndSend(TOPIC_STATUS, snapshot.data());
                });
    }
}
