package tn.iteam.chat.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ChatPresenceSnapshotDto(
        Long roomId,
        String type,
        List<ChatPresenceUserStatusDto> users
) {
}
