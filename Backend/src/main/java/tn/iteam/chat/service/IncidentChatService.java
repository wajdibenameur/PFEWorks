package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.CreateTicketChatRoomRequest;
import tn.iteam.domain.User;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class IncidentChatService {

    private final ChatRoomService roomService;
    private final ChatMessageService messageService;
    private final ChatSystemMessageFactory systemMessageFactory;

    @Transactional
    public ChatRoom createIncidentRoom(CreateTicketChatRoomRequest request, User actor) {
        ChatRoom room = roomService.createTicketRoomByAdmin(
                request.ticketId(),
                request.name(),
                actor.getId(),
                request.inviteUserIds()
        );
        SystemMessageContext context = SystemMessageContext.builder()
                .actorId(actor.getId())
                .actorUsername(actor.getUsername())
                .ticketId(request.ticketId())
                .reason("ROOM_CREATED")
                .metadata(Map.of("roomId", room.getId()))
                .build();
        messageService.sendSystemMessage(room.getId(), systemMessageFactory.roomCreated(context), ChatMessageType.SYSTEM);
        return room;
    }
}

