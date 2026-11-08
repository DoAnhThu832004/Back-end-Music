package com.devteria.identityservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "song_embedding", indexes = {
        @Index(name = "idx_song_normalized_title", columnList = "normalized_title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SongEmbedding {

    @Id
    @Column(name = "song_id", length = 36)
    private String songId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", insertable = false, updatable = false)
    private Song song;

    @Column(name = "search_text", columnDefinition = "TEXT", nullable = false)
    private String searchText;

    @Column(name = "normalized_title", length = 255, nullable = false)
    private String normalizedTitle;

    @Column(name = "embedding_json", columnDefinition = "TEXT", nullable = false)
    private String embeddingJson;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
