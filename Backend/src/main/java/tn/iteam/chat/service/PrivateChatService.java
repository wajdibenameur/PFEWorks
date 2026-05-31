package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.CreatePrivateChatRoomRequest;
import tn.iteam.domain.User;

@Service
@RequiredArgsConstructor
public class PrivateChatService {

    private final ChatRoomService roomService;

    @Transactional
    public ChatRoom createPrivateRoom(CreatePrivateChatRoomRequest request, User actor) {
        Long actorId = actor.getId();
        Long targetUserId = request.targetUserId();
        ChatRoom existing = roomService.findPrivateRoomByUsers(actorId, targetUserId);
        if (existing != null) {
            return existing;
        }
        try {
            return roomService.createPrivateRoom(actorId, targetUserId);
        } catch (DataIntegrityViolationException collision) {
            ChatRoom resolved = roomService.findPrivateRoomByUsers(actorId, targetUserId);
            if (resolved != null) {
                return resolved;
            }
            throw collision;
        }
    }
}
