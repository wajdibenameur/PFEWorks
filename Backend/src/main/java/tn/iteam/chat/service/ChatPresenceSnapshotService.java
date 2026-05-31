package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.dto.ChatPresenceSnapshotDto;
import tn.iteam.chat.dto.ChatPresenceUserStatusDto;
import tn.iteam.domain.User;

@Service
@RequiredArgsConstructor
public class ChatPresenceSnapshotService {

    private final ChatRoomFacadeService facadeService;

    @Transactional(readOnly = true)
    public ChatPresenceSnapshotDto buildSnapshot(Long roomId, User actor) {
        var users = facadeService.participants(roomId, actor).stream()
                .map(participant -> ChatPresenceUserStatusDto.builder()
                        .userId(participant.userId())
                        .status(participant.connected() ? "ONLINE" : "OFFLINE")
                        .build())
                .toList();

        return ChatPresenceSnapshotDto.builder()
                .roomId(roomId)
                .type("PRESENCE_SNAPSHOT")
                .users(users)
                .build();
    }
}
