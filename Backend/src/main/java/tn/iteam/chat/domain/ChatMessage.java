package tn.iteam.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tn.iteam.domain.BaseEntity;

@Entity
@Getter
@Setter
@Table(
        name = "chat_messages",
        indexes = {
                @Index(name = "idx_chat_messages_reply_to", columnList = "reply_to_message_id")
        }
)
public class ChatMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Column(name = "sender_user_id")
    private Long senderUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType = ChatMessageType.USER;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "reply_to_message_id",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_chat_messages_reply_to")
    )
    private ChatMessage replyToMessage;

    @Column(name = "metadata_json", length = 4000)
    private String metadataJson;
}
