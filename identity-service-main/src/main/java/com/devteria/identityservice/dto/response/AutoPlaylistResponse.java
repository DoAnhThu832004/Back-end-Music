package com.devteria.identityservice.dto.response;

import java.util.List;

public class AutoPlaylistResponse {
    private String title;
    private String description;
    private GenreResponse genre;
    private List<SongResponse> songs;

    public AutoPlaylistResponse() {
    }

    public AutoPlaylistResponse(String title, String description, GenreResponse genre, List<SongResponse> songs) {
        this.title = title;
        this.description = description;
        this.genre = genre;
        this.songs = songs;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GenreResponse getGenre() {
        return genre;
    }

    public void setGenre(GenreResponse genre) {
        this.genre = genre;
    }

    public List<SongResponse> getSongs() {
        return songs;
    }

    public void setSongs(List<SongResponse> songs) {
        this.songs = songs;
    }
}
