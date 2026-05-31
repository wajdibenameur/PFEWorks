package tn.iteam.notification;

import lombok.Builder;
import tn.iteam.enums.NotificationSeverity;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Builder
public record NotificationMessage(
        Long notificationId,
        String eventId,
        String eventType,
        NotificationSeverity severity,
        String title,
        String message,
        Long recipientId,
        String recipientUsername,
        String recipientEmail,
        String recipientRole,
        String entityType,
        Long entityId,
        String actionUrl,
        Instant createdAt,
        String templateName,
        String emailSubject,
        Map<String, Object> attributes,
        Set<NotificationChannelType> channels
) {
    public boolean routesTo(NotificationChannelType channelType) {
        return channels != null && channels.contains(channelType);
    }
}
