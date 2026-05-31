package tn.iteam.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.domain.BaseEntity;

import java.time.Instant;

@Entity
@Getter
@Setter
@Table(
        name = "chat_rooms",
        indexes = {
                @Index(name = "idx_chat_rooms_status_closed_at", columnList = "status,closed_at"),
                @Index(name = "idx_chat_rooms_status_archived_at", columnList = "status,archived_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_rooms_ticket_id", columnNames = {"ticket_id"}),
                @UniqueConstraint(name = "uk_chat_rooms_private_key", columnNames = {"private_chat_key"})
        }
)
public class ChatRoom extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    private ChatRoomType roomType = ChatRoomType.INCIDENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRoomStatus status = ChatRoomStatus.OPEN;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "private_chat_key", length = 100)
    private String privateChatKey;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
