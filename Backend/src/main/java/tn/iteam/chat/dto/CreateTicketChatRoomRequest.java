package tn.iteam.chat.dto;

import java.util.List;

public record CreateTicketChatRoomRequest(
        Long ticketId,
        String name,
        List<Long> inviteUserIds
) {
}
