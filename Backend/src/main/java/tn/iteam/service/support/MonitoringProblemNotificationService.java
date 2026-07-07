package tn.iteam.service.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;
import tn.iteam.enums.RoleName;
import tn.iteam.notification.NotificationFactory;
import tn.iteam.notification.NotificationOrchestrator;
import tn.iteam.repository.NotificationRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.service.NotificationService;
import tn.iteam.util.MonitoringConstants;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringProblemNotificationService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_REMINDER_INTERVAL_SECONDS = 8 * 60 * 60L;

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final NotificationFactory notificationFactory;
    private final NotificationOrchestrator notificationOrchestrator;

    @Value("${app.notifications.monitoring.reminder-enabled:true}")
    private boolean reminderEnabled;

    @Value("${app.notifications.monitoring.reminder-interval-seconds:1800}")
    private long reminderIntervalSeconds;

    public void notifySuperadminsForProblem(
            String source,
            String problemId,
            String description,
            String severity,
            String resourceRef,
            Long startedAtEpochSeconds,
            boolean reactivated
    ) {
        if (!isNotificationSeverity(severity) || isBlank(problemId)) {
            return;
        }

        long occurrenceStartedAt = startedAtEpochSeconds != null && startedAtEpochSeconds > 0
                ? startedAtEpochSeconds
                : Instant.now().getEpochSecond();
        List<User> superadmins = userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN);
        if (superadmins.isEmpty()) {
            return;
        }

        String normalizedSource = normalizeSource(source);
        String normalizedResource = !isBlank(resourceRef) ? resourceRef : problemId;
        String eventType = reactivated ? "RECENT_MONITORING_PROBLEM" : "NEW_MONITORING_PROBLEM";
        String eventId = eventType + ":" + normalizedSource + ":" + problemId + ":" + occurrenceStartedAt;
        String title = reactivated
                ? "Recent monitoring problem detected - " + normalizedSource
                : "New monitoring problem detected - " + normalizedSource;
        String message = buildMessage(normalizedSource, normalizedResource, severity, description, occurrenceStartedAt, reactivated);
        NotificationSeverity notificationSeverity = toNotificationSeverity(severity);
        String actionUrl = actionUrlFor(normalizedSource);

        dispatchToSuperadmins(superadmins, normalizedSource, problemId, title, message, eventType, eventId, notificationSeverity, actionUrl);
    }

    public void notifySuperadminsForProblemReminder(
            String source,
            String problemId,
            String description,
            String severity,
            String resourceRef,
            Long startedAtEpochSeconds,
            Long observedAtEpochSeconds
    ) {
        if (!reminderEnabled || !isNotificationSeverity(severity) || isBlank(problemId)) {
            return;
        }

        long occurrenceStartedAt = startedAtEpochSeconds != null && startedAtEpochSeconds > 0
                ? startedAtEpochSeconds
                : 0L;
        long observedAt = observedAtEpochSeconds != null && observedAtEpochSeconds > 0
                ? observedAtEpochSeconds
                : Instant.now().getEpochSecond();
        long interval = Math.max(reminderIntervalSeconds, DEFAULT_REMINDER_INTERVAL_SECONDS);
        if (occurrenceStartedAt <= 0 || observedAt - occurrenceStartedAt < interval) {
            return;
        }

        long reminderSlot = (observedAt - occurrenceStartedAt) / interval;
        if (reminderSlot <= 0) {
            return;
        }

        List<User> superadmins = userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN);
        if (superadmins.isEmpty()) {
            return;
        }

        String normalizedSource = normalizeSource(source);
        String normalizedResource = !isBlank(resourceRef) ? resourceRef : problemId;
        String eventType = "REMINDER_MONITORING_PROBLEM";
        String eventId = eventType + ":" + normalizedSource + ":" + problemId + ":" + occurrenceStartedAt + ":" + reminderSlot;
        String title = "Monitoring problem still active - " + normalizedSource;
        String message = "PROBLEM_REMINDER"
                + " | Source=" + normalizedSource
                + " | Resource=" + safe(normalizedResource)
                + " | Severity=" + safe(severity)
                + " | StartedAt=" + formatTimestamp(occurrenceStartedAt)
                + " | LastObservedAt=" + formatTimestamp(observedAt)
                + " | Description=" + safe(description);
        NotificationSeverity notificationSeverity = toNotificationSeverity(severity);
        String actionUrl = actionUrlFor(normalizedSource);

        dispatchToSuperadmins(superadmins, normalizedSource, problemId, title, message, eventType, eventId, notificationSeverity, actionUrl);
    }

    public void notifySuperadminsForProblemResolved(
            String source,
            String problemId,
            String description,
            String severity,
            String resourceRef,
            Long startedAtEpochSeconds,
            Long resolvedAtEpochSeconds
    ) {
        if (!isNotificationSeverity(severity) || isBlank(problemId)) {
            return;
        }

        long occurrenceStartedAt = startedAtEpochSeconds != null && startedAtEpochSeconds > 0
                ? startedAtEpochSeconds
                : Instant.now().getEpochSecond();
        long resolvedAt = resolvedAtEpochSeconds != null && resolvedAtEpochSeconds > 0
                ? resolvedAtEpochSeconds
                : Instant.now().getEpochSecond();

        List<User> superadmins = userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN);
        if (superadmins.isEmpty()) {
            return;
        }

        String normalizedSource = normalizeSource(source);
        String normalizedResource = !isBlank(resourceRef) ? resourceRef : problemId;
        String eventType = "RESOLVED_MONITORING_PROBLEM";
        String eventId = eventType + ":" + normalizedSource + ":" + problemId + ":" + occurrenceStartedAt;
        String title = "Monitoring problem resolved - " + normalizedSource;
        String message = "PROBLEM_RESOLVED"
                + " | Source=" + normalizedSource
                + " | Resource=" + safe(normalizedResource)
                + " | Severity=" + safe(severity)
                + " | StartedAt=" + formatTimestamp(occurrenceStartedAt)
                + " | ResolvedAt=" + formatTimestamp(resolvedAt)
                + " | Description=" + safe(description);
        NotificationSeverity notificationSeverity = NotificationSeverity.INFO;
        String actionUrl = actionUrlFor(normalizedSource);

        dispatchToSuperadmins(superadmins, normalizedSource, problemId, title, message, eventType, eventId, notificationSeverity, actionUrl);
    }

    private void dispatchToSuperadmins(
            List<User> superadmins,
            String normalizedSource,
            String problemId,
            String title,
            String message,
            String eventType,
            String eventId,
            NotificationSeverity notificationSeverity,
            String actionUrl
    ) {

        for (User target : superadmins) {
            if (target == null || isBlank(target.getUsername())) {
                continue;
            }
            if (target.getId() != null
                    && notificationRepository.findByRecipientIdAndEventId(target.getId(), eventId).isPresent()) {
                continue;
            }

            try {
                NotificationEntity persisted = notificationService.createForRecipient(
                        target,
                        title,
                        message,
                        eventType,
                        eventId,
                        notificationSeverity,
                        NotificationEntityType.MONITORING_ALERT,
                        null,
                        actionUrl
                );
                notificationOrchestrator.dispatch(notificationFactory.createPersistedNotification(persisted, target));
            } catch (Exception exception) {
                log.warn("Unable to dispatch monitoring notification source={} problemId={} recipient={}: {}",
                        normalizedSource,
                        problemId,
                        target.getUsername(),
                        exception.getMessage(),
                        exception);
            }
        }
    }

    private boolean isNotificationSeverity(String severity) {
        if (isBlank(severity)) {
            return false;
        }
        try {
            return Integer.parseInt(severity.trim()) > 3;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private NotificationSeverity toNotificationSeverity(String severity) {
        try {
            return Integer.parseInt(severity.trim()) >= 5
                    ? NotificationSeverity.CRITICAL
                    : NotificationSeverity.WARNING;
        } catch (NumberFormatException exception) {
            return NotificationSeverity.WARNING;
        }
    }

    private String buildMessage(
            String source,
            String resourceRef,
            String severity,
            String description,
            long detectedAt,
            boolean reactivated
    ) {
        return (reactivated ? "RECENT_PROBLEM" : "NEW_PROBLEM")
                + " | Source=" + source
                + " | Resource=" + safe(resourceRef)
                + " | Severity=" + safe(severity)
                + " | DetectedAt=" + formatTimestamp(detectedAt)
                + " | Description=" + safe(description);
    }

    private String actionUrlFor(String source) {
        return switch (source) {
            case MonitoringConstants.SOURCE_SNMP -> "/monitoring/snmp";
            case MonitoringConstants.SOURCE_ZABBIX -> "/monitoring/zabbix";
            default -> "/monitoring";
        };
    }

    private String normalizeSource(String source) {
        if (isBlank(source)) {
            return MonitoringConstants.SOURCE_ZABBIX;
        }
        String normalized = source.trim().toUpperCase();
        return "SNMP".equals(normalized) ? MonitoringConstants.SOURCE_SNMP : normalized;
    }

    private String formatTimestamp(long epochSeconds) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);
    }

    private String safe(String value) {
        return isBlank(value) ? "-" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
