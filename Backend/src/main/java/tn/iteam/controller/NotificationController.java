package tn.iteam.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.iteam.dto.NotificationResponseDTO;
import tn.iteam.dto.NotificationUnreadCountDTO;
import tn.iteam.mapper.NotificationMapper;
import tn.iteam.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    @GetMapping
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Page<NotificationResponseDTO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        String[] sortParts = sort.split(",");
        String sortField = sortParts.length > 0 ? sortParts[0] : "createdAt";
        Sort.Direction direction = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(direction, sortField));
        return ResponseEntity.ok(notificationService.getCurrentUserNotifications(pageable).map(notificationMapper::toResponse));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<NotificationUnreadCountDTO> unreadCount() {
        return ResponseEntity.ok(new NotificationUnreadCountDTO(notificationService.getCurrentUserUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<NotificationResponseDTO> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationMapper.toResponse(notificationService.markAsRead(id)));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.hasPermission(authentication, T(tn.iteam.enums.Permission).VIEW_TICKETS)")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        notificationService.archive(id);
        return ResponseEntity.noContent().build();
    }
}

