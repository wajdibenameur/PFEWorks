package tn.iteam.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.chat.dto.ChatMessageDto;
import tn.iteam.chat.service.ChatMessageService;
import tn.iteam.security.AuthenticatedUserService;

@RestController
@RequestMapping("/api/chat/rooms/{roomId}/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Page<ChatMessageDto>> messages(@PathVariable Long roomId, Pageable pageable) {
        var user = authenticatedUserService.getCurrentUser();
        return ResponseEntity.ok(chatMessageService.getMessages(roomId, user.getId(), pageable));
    }
}

