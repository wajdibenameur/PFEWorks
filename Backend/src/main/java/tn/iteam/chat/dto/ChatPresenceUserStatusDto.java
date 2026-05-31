package tn.iteam.chat.dto;

import lombok.Builder;

@Builder
public record ChatPresenceUserStatusDto(
        Long userId,
        String status
) {
}
