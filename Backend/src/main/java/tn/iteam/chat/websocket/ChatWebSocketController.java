package tn.iteam.chat.websocket;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import tn.iteam.chat.domain.ChatMessageType;
import tn.iteam.chat.dto.ChatPresenceSnapshotRequest;
import tn.iteam.chat.dto.SendChatMessageRequest;
import tn.iteam.chat.service.ChatMessageService;
import tn.iteam.chat.service.ChatPresenceSnapshotService;
import tn.iteam.security.AuthenticatedUserService;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final ChatPresenceSnapshotService presenceSnapshotService;
    private final ChatWebSocketPublisher chatWebSocketPublisher;
    private final AuthenticatedUserService authenticatedUserService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Valid SendChatMessageRequest request, Authentication authentication, Principal principal) {
        String principalName = principal != null ? principal.getName() : null;
        log.info("[CHAT WS] sendMessage received roomId={} principal={} authPresent={}",
                request.roomId(), principalName, authentication != null);

        var currentUser = authentication != null
                ? authenticatedUserService.getCurrentUser(authentication)
                : authenticatedUserService.getCurrentUserByUsername(principalName);
        ChatMessageType type = request.messageType() == null || request.messageType().isBlank()
                ? ChatMessageType.USER
                : ChatMessageType.valueOf(request.messageType().trim().toUpperCase());
        chatMessageService.sendUserMessage(request.roomId(), currentUser.getId(), request.content(), type, request.replyToMessageId());
        log.info("[CHAT WS] sendMessage persisted roomId={} senderUserId={} type={}",
                request.roomId(), currentUser.getId(), type);
    }

    @MessageMapping("/chat.presence.snapshot")
    public void requestPresenceSnapshot(
            @Valid ChatPresenceSnapshotRequest request,
            Authentication authentication,
            Principal principal
    ) {
        String principalName = principal != null ? principal.getName() : null;
        var currentUser = authentication != null
                ? authenticatedUserService.getCurrentUser(authentication)
                : authenticatedUserService.getCurrentUserByUsername(principalName);
        var snapshot = presenceSnapshotService.buildSnapshot(request.roomId(), currentUser);
        chatWebSocketPublisher.publishPresenceSnapshot(currentUser.getUsername(), snapshot);
    }
}
