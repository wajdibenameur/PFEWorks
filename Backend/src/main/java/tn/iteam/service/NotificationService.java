package tn.iteam.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;

public interface NotificationService {
    Page<NotificationEntity> getCurrentUserNotifications(Pageable pageable);

    long getCurrentUserUnreadCount();

    NotificationEntity markAsRead(Long notificationId);

    int markAllAsRead();

    void archive(Long notificationId);

    NotificationEntity createForRecipient(
            User recipient,
            String title,
            String message,
            String eventType,
            String eventId,
            NotificationSeverity severity,
            NotificationEntityType entityType,
            Long entityId,
            String actionUrl
    );
}

