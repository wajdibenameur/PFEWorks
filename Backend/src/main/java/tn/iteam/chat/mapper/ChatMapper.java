package tn.iteam.chat.mapper;

import org.springframework.stereotype.Component;
import tn.iteam.chat.domain.ChatMessage;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.ChatMessageDto;
import tn.iteam.chat.dto.ChatRoomDto;

@Component
public class ChatMapper {
    public ChatRoomDto toRoomDto(ChatRoom room) {
        return ChatRoomDto.builder()
                .id(room.getId())
                .roomType(room.getRoomType().name())
                .status(room.getStatus().name())
                .name(room.getName())
                .ticketId(room.getTicketId())
                .closedAt(room.getClosedAt())
                .archived(room.isArchived())
                .archivedAt(room.getArchivedAt())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    public ChatMessageDto toMessageDto(ChatMessage message, String senderName, String senderRole, Long replyToMessageId) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .senderUserId(message.getSenderUserId())
                .senderName(senderName)
                .senderRole(senderRole)
                .messageType(message.getMessageType().name())
                .content(message.getContent())
                .replyToMessageId(replyToMessageId)
                .createdAt(message.getCreatedAt())
                .build();
    }
}
