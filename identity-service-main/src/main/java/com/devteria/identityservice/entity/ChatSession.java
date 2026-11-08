package com.devteria.identityservice.entity;

import com.devteria.identityservice.enums.AiPersona;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_updated", columnList = "user_id, updatedAt DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private AiPersona persona = AiPersona.GENZ_DJ;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages;

    @PrePersist
    protected void onCreate() {
        if (this.persona == null) {
            this.persona = AiPersona.GENZ_DJ;
        }
        if (this.title == null || this.title.isBlank()) {
            this.title = "Cuộc trò chuyện mới";
        }
    }
}
