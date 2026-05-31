package tn.iteam.chat.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ChatRoomDto(
        Long id,
        String roomType,
        String status,
        String name,
        Long ticketId,
        Instant closedAt,
        boolean archived,
        Instant archivedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
