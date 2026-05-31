package tn.iteam.chat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tn.iteam.chat.domain.ChatMessage;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.domain.ChatRoomType;
import tn.iteam.chat.dto.ChatMessageDto;
import tn.iteam.chat.mapper.ChatMapper;
import tn.iteam.chat.repository.ChatMessageRepository;
import tn.iteam.chat.websocket.ChatWebSocketPublisher;
import tn.iteam.repository.UserRepository;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceIntegrationTest {

    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private ChatRoomService roomService;
    @Mock
    private ChatPolicyService policyService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private UserRepository userRepository;

    private ChatMessageService chatMessageService;

    @BeforeEach
    void setUp() {
        ChatMapper mapper = new ChatMapper();
        ChatWebSocketPublisher publisher = new ChatWebSocketPublisher(messagingTemplate);
        chatMessageService = new ChatMessageService(messageRepository, roomService, policyService, mapper, publisher, userRepository);
    }

    @Test
    void shouldPersistAndPublishUserMessage() {
        ChatRoom room = new ChatRoom();
        room.setId(15L);
        room.setRoomType(ChatRoomType.INCIDENT);
        room.setName("Room");
        room.setTicketId(99L);
        room.setCreatedByUserId(1L);

        when(roomService.findRoomOrThrow(15L)).thenReturn(room);
        doNothing().when(policyService).assertCanSend(room, 9L, ChatMessageType.USER);
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage m = invocation.getArgument(0);
            m.setId(88L);
            m.setCreatedAt(Instant.now());
            return m;
        });

        ChatMessageDto dto = chatMessageService.sendUserMessage(15L, 9L, "hello", ChatMessageType.USER, null);

        assertEquals(88L, dto.id());
        assertEquals(15L, dto.roomId());
        assertEquals("hello", dto.content());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/chat.room.15"), payloadCaptor.capture());
        ChatMessageDto published = (ChatMessageDto) payloadCaptor.getValue();
        assertEquals("hello", published.content());
        assertEquals(15L, published.roomId());
    }
}
