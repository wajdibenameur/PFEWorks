package tn.iteam.chat.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ChatMessageDto(
        Long id,
        Long roomId,
        Long senderUserId,
        String senderName,
        String senderRole,
        String messageType,
        String content,
        Long replyToMessageId,
        Instant createdAt
) {
}
