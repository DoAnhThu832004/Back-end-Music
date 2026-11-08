package com.devteria.identityservice.dto.request;

import com.devteria.identityservice.enums.SongType;
import com.devteria.identityservice.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

public class SongUpdateRequest {
    private String name;
    private String description;
    private Status status;
    private Long duration;
    private LocalDateTime releasedDate;
    private SongType type;
    private List<String> genreIds;

    public SongUpdateRequest() {
    }

    public SongUpdateRequest(String name, String description, Status status, Long duration, LocalDateTime releasedDate, SongType type) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.duration = duration;
        this.releasedDate = releasedDate;
        this.type = type;
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

    public List<String> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(List<String> genreIds) {
        this.genreIds = genreIds;
    }
}
