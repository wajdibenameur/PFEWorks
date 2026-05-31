package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.chat.domain.ChatMessage;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.ChatMessageDto;
import tn.iteam.chat.mapper.ChatMapper;
import tn.iteam.chat.repository.ChatMessageRepository;
import tn.iteam.chat.websocket.ChatWebSocketPublisher;
import tn.iteam.domain.User;
import tn.iteam.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository messageRepository;
    private final ChatRoomService roomService;
    private final ChatPolicyService policyService;
    private final ChatMapper mapper;
    private final ChatWebSocketPublisher publisher;
    private final UserRepository userRepository;

    @Transactional
    public ChatMessageDto sendUserMessage(Long roomId, Long senderUserId, String content, ChatMessageType type, Long replyToMessageId) {
        ChatRoom room = roomService.findRoomOrThrow(roomId);
        ChatMessageType resolvedType = type == null ? ChatMessageType.USER : type;
        policyService.assertCanSend(room, senderUserId, resolvedType);

        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSenderUserId(senderUserId);
        message.setMessageType(resolvedType);
        message.setContent(content != null ? content.trim() : "");
        message.setReplyToMessageId(replyToMessageId);
        ChatMessage saved = messageRepository.save(message);
        ChatMessageDto dto = toDto(saved, resolveUserMap(Set.of(senderUserId)));
        publisher.publishRoomMessage(dto);
        return dto;
    }

    @Transactional
    public ChatMessageDto sendSystemMessage(Long roomId, String content, ChatMessageType type) {
        ChatRoom room = roomService.findRoomOrThrow(roomId);
        ChatMessage message = new ChatMessage();
        message.setRoom(room);
        message.setSenderUserId(null);
        message.setMessageType(type == null ? ChatMessageType.SYSTEM : type);
        message.setContent(content);
        ChatMessage saved = messageRepository.save(message);
        ChatMessageDto dto = toDto(saved, Map.of());
        publisher.publishRoomMessage(dto);
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageDto> getMessages(Long roomId, Long userId, Pageable pageable) {
        policyService.assertCanAccessRoom(roomId, userId);
        Page<ChatMessage> page = messageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        Set<Long> senderIds = page.getContent().stream()
                .map(ChatMessage::getSenderUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, User> userById = resolveUserMap(senderIds);
        return page.map(message -> toDto(message, userById));
    }

    private ChatMessageDto toDto(ChatMessage message, Map<Long, User> userById) {
        User sender = message.getSenderUserId() == null ? null : userById.get(message.getSenderUserId());
        String senderName = sender != null ? sender.getUsername() : null;
        String senderRole = (sender != null && sender.getRole() != null && sender.getRole().getName() != null)
                ? sender.getRole().getName().name()
                : null;
        return mapper.toMessageDto(message, senderName, senderRole, message.getReplyToMessageId());
    }

    private Map<Long, User> resolveUserMap(Set<Long> senderIds) {
        if (senderIds == null || senderIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first, HashMap::new));
    }

}
