package tn.iteam.notification;

import org.springframework.stereotype.Component;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class NotificationFactory {

    public NotificationMessage createTicketNotification(NotificationEntity persisted, User recipient) {
        return NotificationMessage.builder()
                .notificationId(persisted.getId())
                .eventId(persisted.getEventId())
                .eventType(persisted.getEventType())
                .severity(persisted.getSeverity())
                .title(persisted.getTitle())
                .message(persisted.getMessage())
                .recipientId(recipient != null ? recipient.getId() : null)
                .recipientUsername(recipient != null ? recipient.getUsername() : null)
                .recipientEmail(recipient != null ? recipient.getEmail() : null)
                .recipientRole(recipient != null && recipient.getRole() != null && recipient.getRole().getName() != null
                        ? recipient.getRole().getName().name()
                        : null)
                .entityType(persisted.getEntityType().name())
                .entityId(persisted.getEntityId())
                .actionUrl(persisted.getActionUrl())
                .createdAt(persisted.getCreatedAt())
                .templateName("email/notification")
                .emailSubject("[MonitorFlow] " + persisted.getTitle())
                .build();
    }

    public NotificationMessage createAccountLifecycleNotification(
            String toEmail,
            String username,
            String roleName,
            String state,
            String summary,
            String subject,
            String loginUrl
    ) {
        return NotificationMessage.builder()
                .eventId(state + ":" + username + ":" + Instant.now().toEpochMilli())
                .eventType(state)
                .severity(tn.iteam.enums.NotificationSeverity.INFO)
                .title(subject)
                .message(summary)
                .recipientId(recipientIdFromEmail(toEmail))
                .recipientEmail(toEmail != null ? toEmail.trim() : null)
                .templateName("email/account-lifecycle")
                .emailSubject(subject)
                .attributes(Map.of(
                        "username", username == null ? "user" : username,
                        "roleName", roleName == null ? "" : roleName,
                        "state", state,
                        "summary", summary,
                        "loginUrl", loginUrl
                ))
                .channels(Set.of(NotificationChannelType.EMAIL))
                .build();
    }

    private Long recipientIdFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return (long) email.trim().toLowerCase().hashCode();
    }
}

