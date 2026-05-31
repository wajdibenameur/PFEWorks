package tn.iteam.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tn.iteam.chat.repository.ChatParticipantRepository;
import tn.iteam.enums.RoleName;
import tn.iteam.exception.TicketingException;
import tn.iteam.security.AuthenticatedUserService;

@Service
@RequiredArgsConstructor
public class ChatAccessPolicyService {

    private final ChatParticipantRepository participantRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public void assertCanAccessRoom(Long roomId, Long userId) {
        if (isAdminUser()) {
            return;
        }
        boolean participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .map(p -> p.isActive())
                .orElse(false);
        if (!participant) {
            throw new TicketingException(HttpStatus.FORBIDDEN, "CHAT_ACCESS_DENIED", "You are not allowed to access this room");
        }
    }

    public boolean isAdminUser() {
        var roles = authenticatedUserService.getCurrentRoles();
        return roles.contains(RoleName.ADMIN) || roles.contains(RoleName.SUPERADMIN);
    }
}

