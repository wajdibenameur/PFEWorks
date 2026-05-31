package tn.iteam.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendChatMessageRequest(
        @NotNull Long roomId,
        @NotBlank String content,
        String messageType,
        Long replyToMessageId
) {
}
