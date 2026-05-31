package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatParticipantRole;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.enums.Priority;

@Service
@RequiredArgsConstructor
public class ChatTicketLifecycleIntegrator {

    private final ChatRoomService roomService;
    private final ChatParticipantService participantService;
    private final ChatMessageService messageService;

    @Transactional
    public void onTicketCreated(Ticket ticket) {
        if (ticket == null || ticket.getPriority() != Priority.CRITICAL) {
            return;
        }
        Long creatorId = ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : null;
        if (creatorId == null) {
            return;
        }
        ChatRoom room = roomService.createIncidentRoomIfMissing(ticket.getId(), ticket.getTitle(), creatorId);
        messageService.sendSystemMessage(room.getId(), "Incident room created for critical ticket #" + ticket.getId(), ChatMessageType.SYSTEM);
    }

    @Transactional
    public void onTicketAssigned(Ticket ticket, User assignee) {
        if (ticket == null || assignee == null || ticket.getPriority() != Priority.CRITICAL) {
            return;
        }
        ChatRoom room = roomService.createIncidentRoomIfMissing(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getCreatedBy() != null ? ticket.getCreatedBy().getId() : assignee.getId()
        );
        participantService.addParticipant(room, assignee.getId(), ChatParticipantRole.ASSIGNEE);
        messageService.sendSystemMessage(room.getId(), "Ticket assigned to " + assignee.getUsername(), ChatMessageType.SYSTEM);
    }

    @Transactional
    public void onTicketResolved(Ticket ticket) {
        if (ticket == null || ticket.getPriority() != Priority.CRITICAL) {
            return;
        }
        ChatRoom room = roomService.byTicketId(ticket.getId());
        roomService.closeRoomByTicketId(ticket.getId());
        messageService.sendSystemMessage(room.getId(), "Incident resolved. Room closed (read-only).", ChatMessageType.SYSTEM);
    }
}
