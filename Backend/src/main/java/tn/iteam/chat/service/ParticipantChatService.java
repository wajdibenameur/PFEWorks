package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatParticipantRole;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.ChatParticipantDto;
import tn.iteam.domain.User;
import tn.iteam.exception.TicketingException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantChatService {

    private final ChatRoomService roomService;
    private final ChatParticipantService participantService;
    private final ChatAccessPolicyService accessPolicyService;

    @Transactional
    public void joinRoom(Long roomId, User actor) {
        ChatRoom room = roomService.findRoomOrThrow(roomId);
        if (room.isArchived()) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "CHAT_ROOM_ARCHIVED", "Archived room does not accept new participants");
        }
        participantService.addParticipant(room, actor.getId(), ChatParticipantRole.MEMBER);
    }

    @Transactional
    public void leaveRoom(Long roomId, User actor) {
        participantService.leaveRoom(roomId, actor.getId());
    }

    @Transactional(readOnly = true)
    public List<ChatParticipantDto> participants(Long roomId, User actor) {
        accessPolicyService.assertCanAccessRoom(roomId, actor.getId());
        return participantService.activeParticipantDtos(roomId);
    }
}
