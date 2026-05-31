package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;

@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final ChatPolicyService policyService;

    public void assertCanAccessRoom(Long roomId, Long userId) {
        policyService.assertCanAccessRoom(roomId, userId);
    }

    public void assertCanSend(ChatRoom room, Long userId, ChatMessageType type) {
        policyService.assertCanSend(room, userId, type);
    }
}
