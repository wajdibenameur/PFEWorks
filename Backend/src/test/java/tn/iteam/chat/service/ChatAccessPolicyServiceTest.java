package tn.iteam.chat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.chat.domain.ChatParticipant;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.security.AuthenticatedUserService;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessPolicyServiceTest {

    @Mock
    private ChatParticipantRepository participantRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private ChatAccessPolicyService accessPolicyService;

    @Test
    void shouldAllowAdminWithoutParticipantRecord() {
        when(authenticatedUserService.getCurrentRoles()).thenReturn(Set.of(RoleName.ADMIN));
        accessPolicyService.assertCanAccessRoom(1L, 99L);
    }

    @Test
    void shouldRejectRegularUserIfNotParticipant() {
        when(authenticatedUserService.getCurrentRoles()).thenReturn(Collections.emptySet());
        when(participantRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.empty());
        assertThrows(TicketingException.class, () -> accessPolicyService.assertCanAccessRoom(1L, 99L));
    }

    @Test
    void shouldAllowRegularUserIfActiveParticipant() {
        when(authenticatedUserService.getCurrentRoles()).thenReturn(Collections.emptySet());
        ChatParticipant participant = new ChatParticipant();
        participant.setActive(true);
        when(participantRepository.findByRoomIdAndUserId(1L, 99L)).thenReturn(Optional.of(participant));
        accessPolicyService.assertCanAccessRoom(1L, 99L);
    }
}

