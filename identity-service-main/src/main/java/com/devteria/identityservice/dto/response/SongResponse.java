package com.devteria.identityservice.dto.response;

import com.devteria.identityservice.enums.SongType;
import com.devteria.identityservice.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

public class SongResponse {
    private String id;
    private String name;
    private String description;
    private Status status;
    private Long duration;
    private LocalDateTime releasedDate;
    private SongType type;
    private String artistName;
    private String imageUrl;
    private String audioUrl;
    private boolean isFavorite;
    private Long playCount;
    private List<GenreResponse> genres;

    /**
     * Điểm dự đoán được tính bởi Item-Based CF pipeline.
     * Null nếu bài hát đến từ Cold Start (dùng playCount thay thế).
     * Dùng nội bộ bởi Aggregation step để xếp hạng Artist/Album/Playlist.
     */
    private Double predictedScore;

    public SongResponse() {
    }

    public SongResponse(String id, String name, String description, Status status, Long duration, LocalDateTime releasedDate, SongType type,String artistName, String imageUrl, String audioUrl,boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.duration = duration;
        this.releasedDate = releasedDate;
        this.type = type;
        this.artistName = artistName;
        this.imageUrl = imageUrl;
        this.audioUrl = audioUrl;
        this.isFavorite = isFavorite;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Long getDuration() {
        return duration;
    }

    public void setDuration(Long duration) {
        this.duration = duration;
    }

    public LocalDateTime getReleasedDate() {
        return releasedDate;
    }

    public void setReleasedDate(LocalDateTime releasedDate) {
        this.releasedDate = releasedDate;
    }

    public SongType getType() {
        return type;
    }

    public void setType(SongType type) {
        this.type = type;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
    public Long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }

    public List<GenreResponse> getGenres() {
        return genres;
    }

    public void setGenres(List<GenreResponse> genres) {
        this.genres = genres;
    }

    public Double getPredictedScore() {
        return predictedScore;
    }

    public void setPredictedScore(Double predictedScore) {
        this.predictedScore = predictedScore;
    }
}
