package com.devteria.identityservice.dto.response;

import java.util.List;

public class RecommendationResponse {

    private String source;

    private int totalCount;

    private List<SongResponse> songs;

    public RecommendationResponse() {}

    public RecommendationResponse(String source, List<SongResponse> songs) {
        this.source = source;
        this.songs = songs;
        this.totalCount = songs != null ? songs.size() : 0;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public List<SongResponse> getSongs() { return songs; }
    public void setSongs(List<SongResponse> songs) {
        this.songs = songs;
        this.totalCount = songs != null ? songs.size() : 0;
    }
}
