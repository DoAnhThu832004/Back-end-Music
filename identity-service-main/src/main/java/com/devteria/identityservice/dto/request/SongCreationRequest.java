package com.devteria.identityservice.dto.request;

import com.devteria.identityservice.enums.Status;

import java.time.LocalDateTime;
import java.util.List;

public class SongCreationRequest {
    private String name;
    private String description;
    private Long duration;
    private LocalDateTime releasedDate;
    private List<String> genreIds;

    public SongCreationRequest() {
    }

    public SongCreationRequest(String name, String description, Long duration, LocalDateTime releasedDate) {
        this.name = name;
        this.description = description;
        this.duration = duration;
        this.releasedDate = releasedDate;
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

    public List<String> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(List<String> genreIds) {
        this.genreIds = genreIds;
    }
}
