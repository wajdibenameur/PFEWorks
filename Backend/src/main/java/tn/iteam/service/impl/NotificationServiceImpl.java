package tn.iteam.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;
import tn.iteam.exception.TicketingException;
import tn.iteam.repository.NotificationRepository;
import tn.iteam.security.AuthenticatedUserService;
import tn.iteam.service.NotificationService;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthenticatedUserService authenticatedUserService;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationEntity> getCurrentUserNotifications(Pageable pageable) {
        User currentUser = authenticatedUserService.getCurrentUser();
        return notificationRepository.findByRecipientIdAndArchivedFalse(currentUser.getId(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long getCurrentUserUnreadCount() {
        User currentUser = authenticatedUserService.getCurrentUser();
        return notificationRepository.countByRecipientIdAndReadFalseAndArchivedFalse(currentUser.getId());
    }

    @Override
    @Transactional
    public NotificationEntity markAsRead(Long notificationId) {
        User currentUser = authenticatedUserService.getCurrentUser();
        NotificationEntity entity = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        entity.setRead(true);
        return notificationRepository.save(entity);
    }

    @Override
    @Transactional
    public int markAllAsRead() {
        User currentUser = authenticatedUserService.getCurrentUser();
        return notificationRepository.markAllRead(currentUser.getId());
    }

    @Override
    @Transactional
    public void archive(Long notificationId) {
        User currentUser = authenticatedUserService.getCurrentUser();
        NotificationEntity entity = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new TicketingException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found"));
        entity.setArchived(true);
        notificationRepository.save(entity);
    }

    @Override
    @Transactional
    public NotificationEntity createForRecipient(
            User recipient,
            String title,
            String message,
            String eventType,
            String eventId,
            NotificationSeverity severity,
            NotificationEntityType entityType,
            Long entityId,
            String actionUrl
    ) {
        if (recipient == null || eventId == null || eventId.isBlank()) {
            throw new TicketingException(HttpStatus.BAD_REQUEST, "INVALID_NOTIFICATION", "Recipient and eventId are required");
        }

        return notificationRepository.findByRecipientIdAndEventId(recipient.getId(), eventId).orElseGet(() -> {
            NotificationEntity entity = new NotificationEntity();
            entity.setRecipient(recipient);
            entity.setTitle(title);
            entity.setMessage(message);
            entity.setEventType(eventType);
            entity.setEventId(eventId);
            entity.setSeverity(severity != null ? severity : NotificationSeverity.INFO);
            entity.setEntityType(entityType != null ? entityType : NotificationEntityType.SYSTEM);
            entity.setEntityId(entityId);
            entity.setActionUrl(actionUrl);
            entity.setRead(false);
            entity.setArchived(false);
            return notificationRepository.save(entity);
        });
    }
}
