package tn.iteam.chat.dto;

import jakarta.validation.constraints.NotNull;

public record ChatPresenceSnapshotRequest(
        @NotNull Long roomId
) {
}
