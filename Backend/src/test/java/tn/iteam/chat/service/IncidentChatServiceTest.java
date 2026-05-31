package tn.iteam.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.dto.CreateTicketChatRoomRequest;
import tn.iteam.domain.User;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentChatServiceTest {

    @Mock
    private ChatRoomService roomService;
    @Mock
    private ChatMessageService messageService;
    @Mock
    private ChatSystemMessageFactory systemMessageFactory;

    @InjectMocks
    private IncidentChatService incidentChatService;

    @Test
    void shouldCreateIncidentRoomAndSendSystemMessage() {
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("admin");
        CreateTicketChatRoomRequest request = new CreateTicketChatRoomRequest(123L, "Incident #123", List.of(2L, 3L));

        ChatRoom room = new ChatRoom();
        room.setId(42L);
        when(roomService.createTicketRoomByAdmin(123L, "Incident #123", 7L, List.of(2L, 3L))).thenReturn(room);
        when(systemMessageFactory.roomCreated(org.mockito.ArgumentMatchers.any())).thenReturn("created");

        incidentChatService.createIncidentRoom(request, actor);

        verify(messageService).sendSystemMessage(eq(42L), eq("created"), eq(ChatMessageType.SYSTEM));
    }
}

