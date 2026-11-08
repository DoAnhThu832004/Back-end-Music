package com.devteria.identityservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "song_similarity",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"song_a_id", "song_b_id"})
        },
        indexes = {
                @Index(name = "idx_song_a", columnList = "song_a_id"),
                @Index(name = "idx_song_b", columnList = "song_b_id")
        }
)
public class SongSimilarity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "song_a_id", nullable = false)
    private String songAId;

    @Column(name = "song_b_id", nullable = false)
    private String songBId;

    @Column(name = "similarity_score", nullable = false)
    private Double similarityScore;

    @Column(name = "common_user_count", nullable = false)
    private Integer commonUserCount;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SongSimilarity() {}

    public SongSimilarity(String songAId, String songBId, Double similarityScore,
                          Integer commonUserCount, LocalDateTime updatedAt) {
        this.songAId = songAId;
        this.songBId = songBId;
        this.similarityScore = similarityScore;
        this.commonUserCount = commonUserCount;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSongAId() { return songAId; }
    public void setSongAId(String songAId) { this.songAId = songAId; }

    public String getSongBId() { return songBId; }
    public void setSongBId(String songBId) { this.songBId = songBId; }

    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }

    public Integer getCommonUserCount() { return commonUserCount; }
    public void setCommonUserCount(Integer commonUserCount) { this.commonUserCount = commonUserCount; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
