package com.devteria.identityservice.dto.response;

import java.util.List;

public class HomeRecommendationResponse {

    private String source;

    private List<SongResponse> recommendedSongs;

    private List<ArtistResponse> recommendedArtists;

    private List<AlbumResponse> recommendedAlbums;

    private List<PlaylistResponse> recommendedPlaylists;

    public HomeRecommendationResponse() {}

    public HomeRecommendationResponse(
            String source,
            List<SongResponse> recommendedSongs,
            List<ArtistResponse> recommendedArtists,
            List<AlbumResponse> recommendedAlbums,
            List<PlaylistResponse> recommendedPlaylists) {
        this.source = source;
        this.recommendedSongs = recommendedSongs;
        this.recommendedArtists = recommendedArtists;
        this.recommendedAlbums = recommendedAlbums;
        this.recommendedPlaylists = recommendedPlaylists;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public List<SongResponse> getRecommendedSongs() { return recommendedSongs; }
    public void setRecommendedSongs(List<SongResponse> recommendedSongs) { this.recommendedSongs = recommendedSongs; }

    public List<ArtistResponse> getRecommendedArtists() { return recommendedArtists; }
    public void setRecommendedArtists(List<ArtistResponse> recommendedArtists) { this.recommendedArtists = recommendedArtists; }

    public List<AlbumResponse> getRecommendedAlbums() { return recommendedAlbums; }
    public void setRecommendedAlbums(List<AlbumResponse> recommendedAlbums) { this.recommendedAlbums = recommendedAlbums; }

    public List<PlaylistResponse> getRecommendedPlaylists() { return recommendedPlaylists; }
    public void setRecommendedPlaylists(List<PlaylistResponse> recommendedPlaylists) { this.recommendedPlaylists = recommendedPlaylists; }
}
