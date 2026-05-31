package tn.iteam.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.chat.domain.ChatRoom;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.chat.repository.ChatRoomRepository;
import tn.iteam.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServicePrivateRoomTest {

    @Mock
    private ChatRoomRepository roomRepository;
    @Mock
    private ChatParticipantRepository participantRepository;
    @Mock
    private ChatParticipantService participantService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrivateChatKeyGenerator privateChatKeyGenerator;
    @Mock
    private ChatRoomBusinessPolicyService businessPolicyService;
    @Mock
    private ChatRoomStateMachine roomStateMachine;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Test
    void shouldReturnExistingPrivateRoomWithoutCreatingDuplicate() {
        when(privateChatKeyGenerator.generate(5L, 9L)).thenReturn("k");
        ChatRoom existing = new ChatRoom();
        existing.setId(77L);
        when(roomRepository.findByPrivateChatKey("k")).thenReturn(Optional.of(existing));

        ChatRoom result = chatRoomService.createPrivateRoom(5L, 9L);

        assertEquals(77L, result.getId());
        verify(roomRepository, never()).save(org.mockito.ArgumentMatchers.any(ChatRoom.class));
    }
}

