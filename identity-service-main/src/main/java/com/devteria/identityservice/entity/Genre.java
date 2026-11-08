package com.devteria.identityservice.entity;

import jakarta.persistence.*;

import java.util.Set;

@Entity
public class Genre extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String keyG;
    private String name;
    @ManyToMany(mappedBy = "genres")
    private Set<Song> songs;
    @ManyToMany(mappedBy = "artistGenre")
    private Set<Artist> artists;
    public Genre() {
    }

    public Genre(String id, String keyG, String name) {
        this.id = id;
        this.keyG = keyG;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKeyG() {
        return keyG;
    }

    public void setKeyG(String keyG) {
        this.keyG = keyG;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Song> getSongs() {
        return songs;
    }

    public void setSongs(Set<Song> songs) {
        this.songs = songs;
    }
}
