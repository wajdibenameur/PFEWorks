package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;

@Service
@RequiredArgsConstructor
public class ChatPolicyService {

    private final ChatAccessPolicyService accessPolicyService;
    private final ChatRoomBusinessPolicyService businessPolicyService;

    public void assertCanAccessRoom(Long roomId, Long userId) {
        accessPolicyService.assertCanAccessRoom(roomId, userId);
    }

    public void assertCanSend(ChatRoom room, Long userId, ChatMessageType type) {
        assertCanAccessRoom(room.getId(), userId);
        businessPolicyService.assertCanSend(room, type);
    }

    public boolean isAdminUser() {
        return accessPolicyService.isAdminUser();
    }
}
