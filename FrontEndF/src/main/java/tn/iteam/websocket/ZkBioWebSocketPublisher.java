package tn.iteam.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tn.iteam.dto.ZkBioAttendanceDTO;
import tn.iteam.monitoring.snapshot.SnapshotStore;

import java.util.List;

/**
 * Publisher WebSocket pour les donnees ZKBio
 * Publie vers les topics STOMP pour le frontend Angular
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZkBioWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final SnapshotStore snapshotStore;

    public static final String TOPIC_ATTENDANCE = "/topic/zkbio/attendance";
    public static final String TOPIC_DEVICES = "/topic/zkbio/devices";
    public static final String TOPIC_STATUS = "/topic/zkbio/status";
    private static final String DATASET_ATTENDANCE = "attendance";
    private static final String DATASET_DEVICES = "devices";
    private static final String DATASET_STATUS = "status";

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
