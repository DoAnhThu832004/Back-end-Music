package com.devteria.identityservice.entity;

import jakarta.persistence.*;

import java.util.Set;

@Entity
public class Playlist extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String title;
    private String description;
    private String imageUrlP;
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToMany
    @JoinTable(
            name = "playlist_song",
            joinColumns = @JoinColumn(name = "playlist_id"),
            inverseJoinColumns = @JoinColumn(name = "song_id")
    )
    private Set<Song> songPlayList;

    public Playlist() {
    }

    public Playlist(String id, String title, String description,String imageUrlP) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrlP = imageUrlP;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Set<Song> getSongPlayList() {
        return songPlayList;
    }

    public void setSongPlayList(Set<Song> songPlayList) {
        this.songPlayList = songPlayList;
    }

    public String getImageUrlP() {
        return imageUrlP;
    }

    public void setImageUrlP(String imageUrlP) {
        this.imageUrlP = imageUrlP;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
