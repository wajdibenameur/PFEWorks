package tn.iteam.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record NotificationRealtimePayloadDTO(
        Long id,
        String eventId,
        String title,
        String message,
        String eventType,
        String severity,
        String entityType,
        Long entityId,
        String actionUrl,
        Instant createdAt
) {
}

