package tn.iteam.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "chat_participants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_participants_room_user", columnNames = {"room_id", "user_id"})
        }
)
public class ChatParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatParticipantRole role = ChatParticipantRole.MEMBER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private Instant joinedAt = Instant.now();

    @Column
    private Instant leftAt;
}

