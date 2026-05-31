package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatParticipant;
import tn.iteam.chat.domain.ChatParticipantRole;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.ChatParticipantDto;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.websocket.WebSocketSessionMonitor;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatParticipantService {

    private final ChatParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final WebSocketSessionMonitor webSocketSessionMonitor;

    @Transactional
    public void addParticipant(ChatRoom room, Long userId, ChatParticipantRole role) {
        participantRepository.findByRoomIdAndUserId(room.getId(), userId).ifPresentOrElse(existing -> {
            existing.setActive(true);
            existing.setRole(role);
            existing.setLeftAt(null);
            participantRepository.save(existing);
        }, () -> {
            ChatParticipant participant = new ChatParticipant();
            participant.setRoom(room);
            participant.setUserId(userId);
            participant.setRole(role);
            participant.setActive(true);
            participant.setJoinedAt(Instant.now());
            participantRepository.save(participant);
        });
    }

    @Transactional
    public void leaveRoom(Long roomId, Long userId) {
        participantRepository.findByRoomIdAndUserId(roomId, userId).ifPresent(p -> {
            p.setActive(false);
            p.setLeftAt(Instant.now());
            participantRepository.save(p);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatParticipant> activeParticipants(Long roomId) {
        return participantRepository.findByRoomIdAndActiveTrue(roomId);
    }

    @Transactional(readOnly = true)
    public int activeParticipantCount(Long roomId) {
        return participantRepository.findByRoomIdAndActiveTrue(roomId).size();
    }

    @Transactional(readOnly = true)
    public List<ChatParticipantDto> activeParticipantDtos(Long roomId) {
        return participantRepository.findByRoomIdAndActiveTrue(roomId).stream().map(participant -> {
            var user = userRepository.findById(participant.getUserId()).orElse(null);
            String username = user != null ? user.getUsername() : ("user-" + participant.getUserId());
            return ChatParticipantDto.builder()
                    .userId(participant.getUserId())
                    .username(username)
                    .role(participant.getRole().name())
                    .connected(webSocketSessionMonitor.isUserConnected(username))
                    .build();
        }).toList();
    }
}
