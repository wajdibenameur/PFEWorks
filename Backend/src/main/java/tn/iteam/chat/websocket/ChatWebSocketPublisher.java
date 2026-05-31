package tn.iteam.chat.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tn.iteam.chat.dto.ChatMessageDto;
import tn.iteam.chat.dto.ChatPresenceEventDto;
import tn.iteam.chat.dto.ChatPresenceSnapshotDto;

@Component
@RequiredArgsConstructor
public class ChatWebSocketPublisher {
    private final SimpMessagingTemplate ws;

    public void publishRoomMessage(ChatMessageDto message) {
        ws.convertAndSend("/topic/chat.room." + message.roomId(), message);
    }

    public void publishPresence(ChatPresenceEventDto event) {
        ws.convertAndSend("/topic/chat.presence." + event.roomId(), event);
    }

    public void publishUserEvent(String username, Object payload) {
        ws.convertAndSendToUser(username, "/queue/chat", payload);
    }

    public void publishPresenceSnapshot(String username, ChatPresenceSnapshotDto snapshot) {
        ws.convertAndSendToUser(username, "/queue/chat.presence", snapshot);
    }
}
