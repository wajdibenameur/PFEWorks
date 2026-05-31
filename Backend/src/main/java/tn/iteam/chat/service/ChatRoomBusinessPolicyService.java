package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.domain.ChatRoomStatus;
import tn.iteam.chat.domain.ChatRoomType;
import tn.iteam.exception.TicketingException;

@Service
@RequiredArgsConstructor
public class ChatRoomBusinessPolicyService {

    private final ChatParticipantService participantService;

    public void assertCanSend(ChatRoom room, ChatMessageType type) {
        if (room.isArchived() && type != ChatMessageType.SYSTEM) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "CHAT_ROOM_ARCHIVED", "Room is archived (read-only)");
        }
        if (room.getStatus() == ChatRoomStatus.CLOSED && type != ChatMessageType.SYSTEM) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "CHAT_ROOM_CLOSED", "Room is closed (read-only)");
        }
    }

    public void assertRoomConsistency(ChatRoom room) {
        if (room.getRoomType() == ChatRoomType.INCIDENT && room.getTicketId() == null) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_INCIDENT_ROOM", "Incident room must be linked to a ticket");
        }
        if (room.getRoomType() == ChatRoomType.PRIVATE) {
            int activeCount = participantService.activeParticipantCount(room.getId());
            if (activeCount > 2) {
                throw new TicketingException(HttpStatus.BAD_REQUEST, "PRIVATE_ROOM_LIMIT", "Private room can have exactly 2 participants");
            }
        }
    }
}
