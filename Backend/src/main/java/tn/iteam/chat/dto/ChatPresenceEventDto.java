package tn.iteam.chat.dto;

import lombok.Builder;

@Builder
public record ChatPresenceEventDto(
        Long roomId,
        Long userId,
        String username,
        String status
) {
}
