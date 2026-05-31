package tn.iteam.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.chat.dto.CreateTicketChatRoomRequest;
import tn.iteam.chat.dto.CreatePrivateChatRoomRequest;
import tn.iteam.chat.mapper.ChatMapper;
import tn.iteam.chat.service.ChatRoomFacadeService;
import tn.iteam.security.AuthenticatedUserService;

@RestController
@RequestMapping("/api/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomFacadeService facadeService;
    private final ChatMapper mapper;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<?> myRooms() {
        var user = authenticatedUserService.getCurrentUser();
        return ResponseEntity.ok(facadeService.myRooms(user).stream().map(mapper::toRoomDto).toList());
    }

    @GetMapping("/archived")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<?> myArchivedRooms() {
        var user = authenticatedUserService.getCurrentUser();
        return ResponseEntity.ok(facadeService.myArchivedRooms(user).stream().map(mapper::toRoomDto).toList());
    }

    @GetMapping("/by-ticket/{ticketId}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<?> roomByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(mapper.toRoomDto(facadeService.roomByTicket(ticketId)));
    }

    @PostMapping("/{roomId}/join")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Void> joinRoom(@PathVariable Long roomId) {
        var user = authenticatedUserService.getCurrentUser();
        facadeService.joinRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{roomId}/leave")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Void> leaveRoom(@PathVariable Long roomId) {
        var user = authenticatedUserService.getCurrentUser();
        facadeService.leaveRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{roomId}/close")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> closeRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(mapper.toRoomDto(facadeService.closeRoom(roomId)));
    }

    @PutMapping("/{roomId}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> reopenRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(mapper.toRoomDto(facadeService.reopenRoom(roomId)));
    }

    @GetMapping("/{roomId}/participants")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<?> participants(@PathVariable Long roomId) {
        var user = authenticatedUserService.getCurrentUser();
        return ResponseEntity.ok(facadeService.participants(roomId, user));
    }

    @PostMapping("/incident-rooms")
    @PreAuthorize("hasAnyRole('ADMIN','SUPERADMIN')")
    public ResponseEntity<?> createIncidentRoom(
            @RequestBody CreateTicketChatRoomRequest request
    ) {
        var actor = authenticatedUserService.getCurrentUser();
        var room = facadeService.createIncidentRoom(request, actor);
        return ResponseEntity.ok(mapper.toRoomDto(room));
    }

    @PostMapping("/private-rooms")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createPrivateRoom(@RequestBody CreatePrivateChatRoomRequest request) {
        var actor = authenticatedUserService.getCurrentUser();
        var room = facadeService.createPrivateRoom(request, actor);
        return ResponseEntity.ok(mapper.toRoomDto(room));
    }
}
