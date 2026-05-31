package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import tn.iteam.chat.dto.ChatPresenceEventDto;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.chat.websocket.ChatWebSocketPublisher;
import tn.iteam.repository.UserRepository;

import java.security.Principal;

@Component
@RequiredArgsConstructor
public class ChatPresenceBroadcastService {

    private final UserRepository userRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatWebSocketPublisher publisher;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        publishPresenceForEvent(event.getMessage(), "online");
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        publishPresenceForEvent(event.getMessage(), "offline");
    }

    private void publishPresenceForEvent(org.springframework.messaging.Message<?> message, String status) {
        String username = extractUsername(message);
        if (username == null || username.isBlank()) {
            return;
        }
        userRepository.findByUsername(username).ifPresent(user -> participantRepository
                .findActiveByUserIdWithRoomOrderByUpdatedAtDesc(user.getId())
                .forEach(participant -> publisher.publishPresence(ChatPresenceEventDto.builder()
                        .roomId(participant.getRoom().getId())
                        .userId(user.getId())
                        .username(username)
                        .status(status)
                        .build())));
    }

    private String extractUsername(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return null;
        }
        Principal principal = accessor.getUser();
        return principal != null ? principal.getName() : null;
    }
}
