package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.ChatParticipantDto;
import tn.iteam.chat.dto.CreatePrivateChatRoomRequest;
import tn.iteam.chat.dto.CreateTicketChatRoomRequest;
import tn.iteam.domain.User;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomFacadeService {

    private final ChatRoomService roomService;
    private final IncidentChatService incidentChatService;
    private final PrivateChatService privateChatService;
    private final ParticipantChatService participantChatService;

    @Transactional
    public ChatRoom createIncidentRoom(CreateTicketChatRoomRequest request, User actor) {
        return incidentChatService.createIncidentRoom(request, actor);
    }

    @Transactional
    public ChatRoom createPrivateRoom(CreatePrivateChatRoomRequest request, User actor) {
        return privateChatService.createPrivateRoom(request, actor);
    }

    @Transactional
    public void joinRoom(Long roomId, User actor) {
        participantChatService.joinRoom(roomId, actor);
    }

    @Transactional
    public void leaveRoom(Long roomId, User actor) {
        participantChatService.leaveRoom(roomId, actor);
    }

    @Transactional
    public ChatRoom closeRoom(Long roomId) {
        return roomService.closeRoom(roomId);
    }

    @Transactional
    public ChatRoom reopenRoom(Long roomId) {
        return roomService.reopenRoom(roomId);
    }

    @Transactional(readOnly = true)
    public List<tn.iteam.chat.domain.ChatRoom> myRooms(User actor) {
        return roomService.roomsForUser(actor.getId());
    }

    @Transactional
    public List<tn.iteam.chat.domain.ChatRoom> myArchivedRooms(User actor) {
        return roomService.archivedRoomsForUser(actor.getId());
    }

    @Transactional(readOnly = true)
    public ChatRoom roomByTicket(Long ticketId) {
        return roomService.byTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public List<ChatParticipantDto> participants(Long roomId, User actor) {
        return participantChatService.participants(roomId, actor);
    }
}
