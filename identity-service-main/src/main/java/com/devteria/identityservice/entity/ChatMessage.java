package com.devteria.identityservice.entity;

import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_created", columnList = "session_id, createdAt ASC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16)")
    private MessageSender sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private MessageStatus status = MessageStatus.COMPLETE;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_song_id")
    private Song currentSong;

    @Column(name = "token_count")
    @Builder.Default
    private int tokenCount = 0;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = MessageStatus.COMPLETE;
        }
    }
}
