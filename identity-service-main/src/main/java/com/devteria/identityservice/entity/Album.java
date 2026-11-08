package com.devteria.identityservice.entity;

import com.devteria.identityservice.enums.Status;
import jakarta.persistence.*;

import java.util.Set;

@Entity
public class Album extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    private String description;
    private Status status;
    private String imageUrlA;
    @OneToMany(mappedBy = "album")
    private Set<Song> songs;

    @ManyToMany
    @JoinTable(
            name = "artist_album",
            joinColumns = @JoinColumn(name = "album_id"),
            inverseJoinColumns = @JoinColumn(name = "artist_id")
    )
    private Set<Artist> artist;
    public Album() {
    }

    public Album(String id, String name, String description, Status status, String imageUrlA) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.status = status;
        this.imageUrlA = imageUrlA;
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

    public String getImageUrlA() {
        return imageUrlA;
    }

    public void setImageUrlA(String imageUrlA) {
        this.imageUrlA = imageUrlA;
    }

    public Set<Song> getSongs() {
        return songs;
    }

    public void setSongs(Set<Song> songs) {
        this.songs = songs;
    }

    public Set<Artist> getArtist() {
        return artist;
    }

    public void setArtist(Set<Artist> artist) {
        this.artist = artist;
    }
}
