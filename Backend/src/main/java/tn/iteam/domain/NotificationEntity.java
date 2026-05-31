package tn.iteam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.enums.NotificationEntityType;
import tn.iteam.enums.NotificationSeverity;

@Entity
@Getter
@Setter
@Table(name = "notifications")
public class NotificationEntity extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1500)
    private String message;

    @Column(nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 100)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationEntityType entityType = NotificationEntityType.SYSTEM;

    @Column
    private Long entityId;

    @Column(length = 255)
    private String actionUrl;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private boolean archived = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;
}
