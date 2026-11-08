package com.devteria.identityservice.entity;

import com.devteria.identityservice.enums.SongType;
import com.devteria.identityservice.enums.Status;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
public class Song extends AbstractAuditEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Long duration;
    private LocalDateTime releasedDate;
    private SongType type;
    private String imageUrl;
    private String audioUrl;
    @ManyToOne
    @JoinColumn(name = "year_id")
    private Year year;
    @ManyToMany
    @JoinTable(
        name = "song_genre",
        joinColumns = @JoinColumn(name = "song_id"),
        inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres;

    @ManyToMany(mappedBy = "songPlayList")
    private Set<Playlist> playlists;
    @ManyToOne
    @JoinColumn(name = "album_id")
    private Album album;
    @ManyToMany(mappedBy = "song")
    private Set<Artist> artists;

    @Column(name = "play_count", columnDefinition = "bigint default 0")
    private Long playCount = 0L;
    public Song() {
    }

    public Song(String id, String name, String description, Status status, Long duration, LocalDateTime releasedDate, SongType type, String imageUrl, String audioUrl) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.duration = duration;
        this.releasedDate = releasedDate;
        this.type = type;
        this.imageUrl = imageUrl;
        this.audioUrl = audioUrl;
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

    public Year getYear() {
        return year;
    }

    public void setYear(Year year) {
        this.year = year;
    }

    public Set<Genre> getGenres() {
        return genres;
    }

    public void setGenres(Set<Genre> genres) {
        this.genres = genres;
    }

    public Set<Playlist> getPlaylists() {
        return playlists;
    }

    public void setPlaylists(Set<Playlist> playlists) {
        this.playlists = playlists;
    }

    public Album getAlbum() {
        return album;
    }

    public void setAlbum(Album album) {
        this.album = album;
    }

    public Set<Artist> getArtists() {
        return artists;
    }

    public void setArtists(Set<Artist> artists) {
        this.artists = artists;
    }

    // Bổ sung các phương thức Getter và Setter cho thuộc tính mới
    public Long getPlayCount() {
        return playCount;
    }

    public void setPlayCount(Long playCount) {
        this.playCount = playCount;
    }
}
