package tn.iteam.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.Ticket;
import tn.iteam.domain.User;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;
import tn.iteam.enums.Priority;
import tn.iteam.notification.NotificationFactory;
import tn.iteam.notification.NotificationOrchestrator;
import tn.iteam.enums.RoleName;
import tn.iteam.repository.UserRepository;
import tn.iteam.service.NotificationService;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketNotificationService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;
    private final NotificationOrchestrator notificationOrchestrator;

    public void notifyImportantTicketCreated(Ticket ticket, User actor) {
        if (ticket == null || !isImportant(ticket)) {
            return;
        }
        List<User> superadmins = userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN);
        for (User target : superadmins) {
            if (!isEligibleRecipient(target, actor)) {
                continue;
            }
            dispatch(target, ticket, "NEW_IMPORTANT_TICKET", NotificationSeverity.CRITICAL, "Nouveau ticket important");
        }
    }

    public void notifyAssignment(Ticket ticket, User actor) {
        if (ticket == null || ticket.getAssignedTo() == null) {
            return;
        }
        User assignee = ticket.getAssignedTo();
        if (!isEligibleRecipient(assignee, actor)) {
            return;
        }
        dispatch(assignee, ticket, "TICKET_ASSIGNED", NotificationSeverity.WARNING, "Ticket assigne");
    }

    public void notifyStatusOrValidation(Ticket ticket, User actor, String eventType) {
        if (ticket == null) {
            return;
        }
        List<User> superadmins = userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN);
        for (User target : superadmins) {
            if (!isEligibleRecipient(target, actor)) {
                continue;
            }
            dispatch(target, ticket, eventType, NotificationSeverity.INFO, "Mise a jour ticket");
        }

        if (ticket.getAssignedTo() != null && isEligibleRecipient(ticket.getAssignedTo(), actor)) {
            dispatch(ticket.getAssignedTo(), ticket, eventType, NotificationSeverity.INFO, "Suivi ticket");
        }
    }

    private boolean isImportant(Ticket ticket) {
        return ticket.getPriority() == Priority.HIGH || ticket.getPriority() == Priority.CRITICAL;
    }

    private boolean isEligibleRecipient(User target, User actor) {
        if (target == null || target.getUsername() == null || target.getUsername().isBlank()) {
            return false;
        }
        return actor == null || !Objects.equals(target.getId(), actor.getId());
    }

    private void dispatch(User user, Ticket ticket, String eventType, NotificationSeverity severity, String title) {
        String eventId = eventType + ":" + ticket.getId() + ":" + safe(ticket.getStatus() != null ? ticket.getStatus().name() : null);
        String actionUrl = "/tickets/tracking?id=" + ticket.getId();
        String message = buildMessage(eventType, ticket);

        NotificationEntity persisted = notificationService.createForRecipient(
                user,
                title,
                message,
                eventType,
                eventId,
                severity,
                NotificationEntityType.TICKET,
                ticket.getId(),
                actionUrl
        );

        notificationOrchestrator.dispatch(notificationFactory.createTicketNotification(persisted, user));
    }

    private String buildMessage(String eventType, Ticket ticket) {
        return eventType
                + " | Ticket #" + ticket.getId()
                + " | " + safe(ticket.getTitle())
                + " | Statut=" + safe(ticket.getStatus() != null ? ticket.getStatus().name() : null)
                + " | Priorite=" + safe(ticket.getPriority() != null ? ticket.getPriority().name() : null);
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
