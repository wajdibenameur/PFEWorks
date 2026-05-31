package tn.iteam.notification.channels;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tn.iteam.dto.NotificationRealtimePayloadDTO;
import tn.iteam.notification.NotificationChannel;
import tn.iteam.notification.NotificationChannelType;
import tn.iteam.notification.NotificationMessage;

@Component
@RequiredArgsConstructor
public class WebSocketNotificationChannel implements NotificationChannel {

    private final SimpMessagingTemplate ws;

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.WEBSOCKET;
    }

    @Override
    public void send(NotificationMessage message) {
        if (message == null || message.recipientUsername() == null || message.recipientUsername().isBlank()) {
            return;
        }
        NotificationRealtimePayloadDTO payload = NotificationRealtimePayloadDTO.builder()
                .id(message.notificationId())
                .eventId(message.eventId())
                .title(message.title())
                .message(message.message())
                .eventType(message.eventType())
                .severity(message.severity() != null ? message.severity().name() : "INFO")
                .entityType(message.entityType())
                .entityId(message.entityId())
                .actionUrl(message.actionUrl())
                .createdAt(message.createdAt())
                .build();
        ws.convertAndSendToUser(message.recipientUsername(), "/queue/notifications", payload);
    }
}

