package com.devteria.identityservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Formula;

import java.util.AbstractList;
import java.util.Set;

@Entity
public class Artist extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    private String description;
    private String imageUrlAr;
    @ManyToMany(mappedBy = "artist")
    private Set<Album> albums;
    @ManyToMany
    @JoinTable(
            name = "artist_song",
            joinColumns = @JoinColumn(name = "artist_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id")
    )
    private Set<Song> song;
    @ManyToMany
    @JoinTable(
            name = "artist_genre",
            joinColumns = @JoinColumn(name = "artist_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> artistGenre;
    @Formula("(SELECT count(*) FROM followers f WHERE f.artist_id = id)")
    private long totalFollowers;
    public Artist() {
    }

    public Artist(String id, String name, String description, String imageUrlAr) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.imageUrlAr = imageUrlAr;
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

    public String getImageUrlAr() {
        return imageUrlAr;
    }

    public void setImageUrlAr(String imageUrlAr) {
        this.imageUrlAr = imageUrlAr;
    }

    public Set<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(Set<Album> albums) {
        this.albums = albums;
    }

    public Set<Song> getSong() {
        return song;
    }

    public void setSong(Set<Song> song) {
        this.song = song;
    }

    public Set<Genre> getArtistGenre() {
        return artistGenre;
    }

    public void setArtistGenre(Set<Genre> artistGenre) {
        this.artistGenre = artistGenre;
    }
    public void addAlbum(Album album) { // đồng bộ  hau chiều trong bộ nhớ
        this.albums.add(album);
        album.getArtist().add(this);
    }
    public void removeAlbum(Album album) {
        this.albums.remove(album);
        album.getArtist().remove(this);
    }
    public void addSong(Song song) {
        this.song.add(song);
        song.getArtists().add(this);
    }
    public void removeSong(Song song) {
        this.song.remove(song);
        song.getArtists().remove(this);
    }

    public long getTotalFollowers() {
        return totalFollowers;
    }

    public void setTotalFollowers(long totalFollowers) {
        this.totalFollowers = totalFollowers;
    }
}
