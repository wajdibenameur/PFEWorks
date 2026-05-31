package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatParticipantRole;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.domain.ChatRoomStatus;
import tn.iteam.chat.domain.ChatRoomType;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.chat.repository.ChatRoomRepository;
import tn.iteam.domain.User;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.UserRepository;

import java.util.List;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository roomRepository;
    private final ChatParticipantRepository participantRepository;
    private final ChatParticipantService participantService;
    private final UserRepository userRepository;
    private final PrivateChatKeyGenerator privateChatKeyGenerator;
    private final ChatRoomBusinessPolicyService businessPolicyService;
    private final ChatRoomStateMachine roomStateMachine;

    @Transactional
    public ChatRoom createIncidentRoomIfMissing(Long ticketId, String title, Long creatorId) {
        return roomRepository.findByTicketId(ticketId).orElseGet(() -> {
            ChatRoom room = new ChatRoom();
            room.setRoomType(ChatRoomType.INCIDENT);
            roomStateMachine.initializeAsOpen(room);
            room.setTicketId(ticketId);
            room.setName("Incident #" + ticketId + " - " + title);
            room.setCreatedByUserId(creatorId);
            ChatRoom saved = roomRepository.save(room);
            participantService.addParticipant(saved, creatorId, ChatParticipantRole.OWNER);
            seedAdmins(saved);
            return saved;
        });
    }

    @Transactional
    public ChatRoom createTicketRoomByAdmin(Long ticketId, String name, Long creatorId, List<Long> inviteUserIds) {
        if (ticketId == null || ticketId <= 0) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_TICKET", "ticketId is required");
        }
        ChatRoom room = roomRepository.findByTicketId(ticketId).orElseGet(() -> {
            ChatRoom next = new ChatRoom();
            next.setRoomType(ChatRoomType.INCIDENT);
            roomStateMachine.initializeAsOpen(next);
            next.setTicketId(ticketId);
            next.setName((name != null && !name.isBlank()) ? name.trim() : ("Ticket #" + ticketId + " Incident Room"));
            next.setCreatedByUserId(creatorId);
            return roomRepository.save(next);
        });

        participantService.addParticipant(room, creatorId, ChatParticipantRole.ADMIN);
        if (inviteUserIds != null) {
            inviteUserIds.stream()
                    .filter(id -> id != null && id > 0)
                    .distinct()
                    .forEach(userId -> participantService.addParticipant(room, userId, ChatParticipantRole.MEMBER));
        }
        return room;
    }

    @Transactional
    public ChatRoom createPrivateRoom(Long requesterUserId, Long targetUserId) {
        if (requesterUserId == null || targetUserId == null || requesterUserId <= 0 || targetUserId <= 0) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_PRIVATE_ROOM", "Both users are required");
        }
        if (requesterUserId.equals(targetUserId)) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_PRIVATE_ROOM", "Cannot open private room with self");
        }

        String privateKey = privateChatKeyGenerator.generate(requesterUserId, targetUserId);
        ChatRoom existingByKey = roomRepository.findByPrivateChatKey(privateKey).orElse(null);
        if (existingByKey != null) {
            return existingByKey;
        }

        ChatRoom room = new ChatRoom();
        room.setRoomType(ChatRoomType.PRIVATE);
        roomStateMachine.initializeAsOpen(room);
        room.setTicketId(null);
        room.setPrivateChatKey(privateKey);
        room.setName("Private chat");
        room.setCreatedByUserId(requesterUserId);
        ChatRoom saved = roomRepository.save(room);
        participantService.addParticipant(saved, requesterUserId, ChatParticipantRole.OWNER);
        participantService.addParticipant(saved, targetUserId, ChatParticipantRole.MEMBER);
        enforcePrivateLimit(saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public ChatRoom findPrivateRoomByUsers(Long userA, Long userB) {
        String privateKey = privateChatKeyGenerator.generate(userA, userB);
        return roomRepository.findByPrivateChatKey(privateKey).orElse(null);
    }

    @Transactional
    public void closeRoomByTicketId(Long ticketId) {
        roomRepository.findByTicketId(ticketId).ifPresent(room -> {
            roomStateMachine.transitionToClosed(room);
            room.setClosedAt(Instant.now());
            roomRepository.save(room);
        });
    }

    @Transactional
    public ChatRoom closeRoom(Long roomId) {
        ChatRoom room = findRoomOrThrow(roomId);
        roomStateMachine.transitionToClosed(room);
        room.setClosedAt(Instant.now());
        room.setArchived(false);
        room.setArchivedAt(null);
        return roomRepository.save(room);
    }

    @Transactional
    public ChatRoom reopenRoom(Long roomId) {
        ChatRoom room = findRoomOrThrow(roomId);
        roomStateMachine.transitionToOpen(room);
        room.setClosedAt(null);
        room.setArchived(false);
        room.setArchivedAt(null);
        return roomRepository.save(room);
    }

    @Transactional
    public int archiveClosedRoomsBefore(Instant cutoff) {
        if (cutoff == null) {
            return 0;
        }
        List<ChatRoom> rooms = roomRepository.findByStatusAndArchivedFalseAndClosedAtBefore(ChatRoomStatus.CLOSED, cutoff);
        for (ChatRoom room : rooms) {
            room.setArchived(true);
            room.setArchivedAt(Instant.now());
        }
        roomRepository.saveAll(rooms);
        return rooms.size();
    }

    public ChatRoom findRoomOrThrow(Long roomId) {
        ChatRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "Chat room not found"));
        return normalizeLegacyLockedStatus(room);
    }

    @Transactional
    public List<ChatRoom> roomsForUser(Long userId) {
        return participantRepository.findActiveByUserIdWithRoomOrderByUpdatedAtDesc(userId)
                .stream()
                .map(p -> p.getRoom())
                .map(this::normalizeLegacyLockedStatus)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<ChatRoom> archivedRoomsForUser(Long userId) {
        return roomRepository.findRoomsByUserIdAndArchived(userId, true).stream()
                .map(this::normalizeLegacyLockedStatus)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatRoom byTicketId(Long ticketId) {
        return roomRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "CHAT_ROOM_NOT_FOUND", "Chat room not found for ticket"));
    }

    @Transactional(readOnly = true)
    public void enforceRoomRules(ChatRoom room) {
        businessPolicyService.assertRoomConsistency(room);
    }

    private void seedAdmins(ChatRoom room) {
        List<User> users = userRepository.findAll();
        users.stream()
                .filter(User::isEnabled)
                .filter(u -> hasRole(u, RoleName.ADMIN) || hasRole(u, RoleName.SUPERADMIN))
                .forEach(u -> participantService.addParticipant(room, u.getId(), ChatParticipantRole.ADMIN));
    }

    private boolean hasRole(User user, RoleName roleName) {
        if (user.getRole() != null && user.getRole().getName() == roleName) {
            return true;
        }
        return user.getRoles() != null && user.getRoles().stream().anyMatch(r -> r != null && r.getName() == roleName);
    }

    private void enforcePrivateLimit(Long roomId) {
        int activeCount = participantService.activeParticipantCount(roomId);
        if (activeCount > 2) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "PRIVATE_ROOM_LIMIT", "Private room can have exactly 2 participants");
        }
    }

    private ChatRoom normalizeLegacyLockedStatus(ChatRoom room) {
        return room;
    }
}
