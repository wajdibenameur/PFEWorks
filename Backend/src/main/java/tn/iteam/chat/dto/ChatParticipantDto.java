package tn.iteam.chat.dto;

import lombok.Builder;

@Builder
public record ChatParticipantDto(
        Long userId,
        String username,
        String role,
        boolean connected
) {
}

