package tn.iteam.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record NotificationResponseDTO(
        Long id,
        String title,
        String message,
        String eventType,
        String eventId,
        String severity,
        String entityType,
        Long entityId,
        String actionUrl,
        boolean read,
        boolean archived,
        Instant createdAt
) {
}

