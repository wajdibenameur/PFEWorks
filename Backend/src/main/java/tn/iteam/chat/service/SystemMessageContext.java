package tn.iteam.chat.service;

import lombok.Builder;

import java.util.Map;

@Builder
public record SystemMessageContext(
        Long actorId,
        String actorUsername,
        Long ticketId,
        String reason,
        Map<String, Object> metadata
) {
}

