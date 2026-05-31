package tn.iteam.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.dto.NotificationResponseDTO;

@Component
public class NotificationMapper {

    public NotificationResponseDTO toResponse(NotificationEntity entity) {
        return NotificationResponseDTO.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .eventType(entity.getEventType())
                .eventId(entity.getEventId())
                .severity(entity.getSeverity().name())
                .entityType(entity.getEntityType().name())
                .entityId(entity.getEntityId())
                .actionUrl(entity.getActionUrl())
                .read(entity.isRead())
                .archived(entity.isArchived())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

