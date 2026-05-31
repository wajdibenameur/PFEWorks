package tn.iteam.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.domain.BaseEntity;

@Entity
@Getter
@Setter
@Table(
        name = "notification_dispatch_records",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_dispatch_event_recipient_channel",
                        columnNames = {"event_id", "recipient_id", "channel"}
                )
        }
)
public class NotificationDispatchRecord extends BaseEntity {

    @Column(name = "event_id", nullable = false, length = 120)
    private String eventId;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationDispatchStatus status;

    @Column(name = "last_error", length = 600)
    private String lastError;
}

