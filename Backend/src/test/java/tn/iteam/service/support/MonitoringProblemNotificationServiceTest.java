package tn.iteam.service.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.iteam.domain.NotificationEntity;
import tn.iteam.domain.User;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;
import tn.iteam.enums.RoleName;
import tn.iteam.notification.NotificationFactory;
import tn.iteam.notification.NotificationMessage;
import tn.iteam.notification.NotificationOrchestrator;
import tn.iteam.repository.NotificationRepository;
import tn.iteam.repository.UserRepository;
import tn.iteam.service.NotificationService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringProblemNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationFactory notificationFactory;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    private MonitoringProblemNotificationService service;

    @BeforeEach
    void setUp() {
        service = new MonitoringProblemNotificationService(
                userRepository,
                notificationRepository,
                notificationService,
                notificationFactory,
                notificationOrchestrator
        );
    }

    @Test
    void notifiesSuperadminsForNewSeverityFourProblem() {
        User superadmin = new User();
        superadmin.setId(7L);
        superadmin.setUsername("superadmin");
        superadmin.setEmail("superadmin@example.com");

        NotificationEntity persisted = new NotificationEntity();
        persisted.setId(99L);
        persisted.setEventId("NEW_MONITORING_PROBLEM:SNMP:OBS-1:1717412400");
        persisted.setEventType("NEW_MONITORING_PROBLEM");
        persisted.setSeverity(NotificationSeverity.WARNING);
        persisted.setEntityType(NotificationEntityType.MONITORING_ALERT);
        persisted.setCreatedAt(Instant.now());
        persisted.setTitle("New monitoring problem detected - SNMP");
        persisted.setMessage("msg");
        persisted.setRecipient(superadmin);

        NotificationMessage message = NotificationMessage.builder()
                .eventId(persisted.getEventId())
                .eventType(persisted.getEventType())
                .recipientUsername(superadmin.getUsername())
                .severity(NotificationSeverity.WARNING)
                .build();

        when(userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN)).thenReturn(List.of(superadmin));
        when(notificationRepository.findByRecipientIdAndEventId(eq(7L), any())).thenReturn(Optional.empty());
        when(notificationService.createForRecipient(
                eq(superadmin),
                eq("New monitoring problem detected - SNMP"),
                any(),
                eq("NEW_MONITORING_PROBLEM"),
                any(),
                eq(NotificationSeverity.WARNING),
                eq(NotificationEntityType.MONITORING_ALERT),
                eq(null),
                eq("/monitoring/snmp")
        )).thenReturn(persisted);
        when(notificationFactory.createPersistedNotification(persisted, superadmin)).thenReturn(message);

        service.notifySuperadminsForProblem(
                "SNMP",
                "OBS-1",
                "SNMP device status is DOWN",
                "4",
                "192.168.1.20",
                1717412400L,
                false
        );

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createForRecipient(
                eq(superadmin),
                eq("New monitoring problem detected - SNMP"),
                messageCaptor.capture(),
                eq("NEW_MONITORING_PROBLEM"),
                eq("NEW_MONITORING_PROBLEM:SNMP:OBS-1:1717412400"),
                eq(NotificationSeverity.WARNING),
                eq(NotificationEntityType.MONITORING_ALERT),
                eq(null),
                eq("/monitoring/snmp")
        );
        assertThat(messageCaptor.getValue()).contains("NEW_PROBLEM");
        assertThat(messageCaptor.getValue()).contains("Severity=4");
        verify(notificationOrchestrator).dispatch(message);
    }

    @Test
    void skipsSeverityThreeAndExistingEvents() {
        User superadmin = new User();
        superadmin.setId(7L);
        superadmin.setUsername("superadmin");

        when(userRepository.findEnabledUsersByRoleName(RoleName.SUPERADMIN)).thenReturn(List.of(superadmin));

        service.notifySuperadminsForProblem(
                "ZABBIX",
                "ZBX-1",
                "Degraded host",
                "3",
                "srv-1",
                1717412400L,
                false
        );

        verify(notificationService, never()).createForRecipient(any(), any(), any(), any(), any(), any(), any(), any(), any());

        NotificationEntity existing = new NotificationEntity();
        when(notificationRepository.findByRecipientIdAndEventId(eq(7L), eq("RECENT_MONITORING_PROBLEM:ZABBIX:ZBX-1:1717412400")))
                .thenReturn(Optional.of(existing));

        service.notifySuperadminsForProblem(
                "ZABBIX",
                "ZBX-1",
                "Host down again",
                "4",
                "srv-1",
                1717412400L,
                true
        );

        verify(notificationService, never()).createForRecipient(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationOrchestrator, never()).dispatch(any());
    }
}
