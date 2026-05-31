package tn.iteam.chat.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.domain.ChatRoomStatus;
import tn.iteam.exception.TicketingException;

@Component
public class ChatRoomStateMachine {

    public void initializeAsOpen(ChatRoom room) {
        room.setStatus(ChatRoomStatus.OPEN);
    }

    public void transitionToClosed(ChatRoom room) {
        if (room.getStatus() == ChatRoomStatus.CLOSED) {
            return;
        }
        if (room.getStatus() != ChatRoomStatus.OPEN) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_ROOM_TRANSITION", "Only OPEN rooms can be closed");
        }
        room.setStatus(ChatRoomStatus.CLOSED);
    }

    public void transitionToOpen(ChatRoom room) {
        if (room.getStatus() == ChatRoomStatus.OPEN) {
            return;
        }
        if (room.getStatus() != ChatRoomStatus.CLOSED) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_CHAT_ROOM_TRANSITION", "Only CLOSED rooms can be reopened");
        }
        room.setStatus(ChatRoomStatus.OPEN);
    }
}
