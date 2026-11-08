package com.devteria.identityservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.devteria.identityservice.dto.request.AlbumUpdateRequest;
import com.devteria.identityservice.dto.request.ArtistCreationRequest;
import com.devteria.identityservice.dto.request.ArtistUpdateRequest;
import com.devteria.identityservice.dto.response.AlbumResponse;
import com.devteria.identityservice.dto.response.ArtistResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Album;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.devteria.identityservice.dto.response.PageResponse;

@Service
public class ArtistService {
    private final ArtistRepository artistRepository;
    private final SongRepository songRepository;
    private final GenreRepository genreRepository;
    private final AlbumRepository albumRepository;
    private final Cloudinary cloudinary;

    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;

    public ArtistService(ArtistRepository artistRepository, SongRepository songRepository, GenreRepository genreRepository, AlbumRepository albumRepository, Cloudinary cloudinary, FollowerRepository followerRepository, UserRepository userRepository) {
        this.artistRepository = artistRepository;
        this.songRepository = songRepository;
        this.genreRepository = genreRepository;
        this.albumRepository = albumRepository;
        this.cloudinary = cloudinary;
        this.followerRepository = followerRepository;
        this.userRepository = userRepository;
    }
    @Transactional
    public ArtistResponse createArtist(ArtistCreationRequest request) {
        Artist artist = toArtist(request);
        return toArtistResponse(artistRepository.save(artist));
    }
    public List<ArtistResponse> getArtists() {
        List<Artist> artists = artistRepository.findAll();
        Set<String> followArtistId = getFollowerArtistIdOfCurrentUser();
        return artists.stream()
                .map( artist -> {
                    ArtistResponse response = toArtistResponse(artist);
                    response.setFollowed(followArtistId.contains(artist.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }
    public ArtistResponse getArtist(String id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        ArtistResponse response = toArtistResponse(artist);
        response.setFollowed(isArtistFollowerByCurrentUser(artist));
        return response;
    }
    public ArtistResponse getArtistName(String name) {
        Artist artist = artistRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        ArtistResponse response = toArtistResponse(artist);
        response.setFollowed(isArtistFollowerByCurrentUser(artist));
        return response;
    }
    public List<ArtistResponse> searchArtist(String key) {
        if(key == null || key.trim().isEmpty()) {
            return List.of();
        }
        List<Artist> artists = artistRepository.findByNameContainingIgnoreCase(key);
        Set<String> followArtistId = getFollowerArtistIdOfCurrentUser();
        return artists.stream()
                .map( artist -> {
                    ArtistResponse response = toArtistResponse(artist);
                    response.setFollowed(followArtistId.contains(artist.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }
    public PageResponse<ArtistResponse> searchArtistsForAdmin(String key, int page, int size) {
        if (key == null || key.trim().isEmpty()) {
            return new PageResponse<>(page, 0, size, 0, Collections.emptyList());
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Artist> artistPage = artistRepository.findByNameContainingIgnoreCase(key, pageable);

        Set<String> followArtistId = getFollowerArtistIdOfCurrentUser();
        List<ArtistResponse> artistResponses = artistPage.getContent().stream()
                .map(artist -> {
                    ArtistResponse response = toArtistResponse(artist);
                    response.setFollowed(followArtistId.contains(artist.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(
                page,
                artistPage.getTotalPages(),
                artistPage.getSize(),
                artistPage.getTotalElements(),
                artistResponses
        );
    }
    @Transactional
    public ArtistResponse updateArtist(String id, ArtistUpdateRequest request) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(()-> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        mapRequestToArtist(artist,request);
        return toArtistResponse(artistRepository.save(artist));
    }
    @Transactional
    public void deleteArtist(String id) {
        artistRepository.deleteById(id);
    }
    @Transactional
    public void addArtistToSong(String artistId, String songId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));
        artist.addSong(song);
        artistRepository.save(artist);
    }
    @Transactional
    public void removeSongToArtist(String artistId,String songId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(()-> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        Song song = songRepository.findById(songId)
                .orElseThrow(()-> new AppException(ErrorCode.SONG_NOT_EXISTED));
        artist.removeSong(song);
        artistRepository.save(artist);
    }
    @Transactional
    public void addAlbumToArtist(String artistId, String albumId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AppException(ErrorCode.ALBUM_NOT_EXISTED));
        artist.addAlbum(album);
        artistRepository.save(artist);
    }
    @Transactional
    public void removeAlbumToArtist(String artistId, String albumId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AppException(ErrorCode.ALBUM_NOT_EXISTED));
        artist.removeAlbum(album);
        artistRepository.save(artist);
    }
    private void mapRequestToArtist(Artist artist, ArtistUpdateRequest request) {
        if(request == null) return;
        if(request.getName() != null && !request.getName().isEmpty()) {
            artist.setName(request.getName());
        }
        if(request.getDescription() != null && !request.getDescription().isEmpty()) {
            artist.setDescription(request.getDescription());
        }
    }
    public Artist toArtist(ArtistCreationRequest request) {
        if(request == null) return null;
        Artist artist = new Artist();
        artist.setName(request.getName());
        artist.setDescription(request.getDescription());
        return artist;
    }
    public static ArtistResponse toArtistResponse(Artist artist) {
        if (artist == null) return null;
        ArtistResponse response = new ArtistResponse();
        response.setId(artist.getId());
        response.setName(artist.getName());
        response.setDescription(artist.getDescription());
        response.setImageUrlAr(artist.getImageUrlAr());
        response.setTotalFollowers(artist.getTotalFollowers());
        response.setFollowed(false);
        if (artist.getSong() != null) {
            Set<SongResponse> songResponses = new HashSet<>();
            for (Song song : artist.getSong()) {
                SongResponse sr = SongService.toSongResponse(song);
                if (sr != null) songResponses.add(sr);
            }
            response.setSongs(songResponses);
        } else {
            response.setSongs(new HashSet<>());
        }
        if (artist.getAlbums() != null) {
            Set<AlbumResponse> albumResponses = new HashSet<>();
            for (Album album : artist.getAlbums()) {
                AlbumResponse sr = AlbumService.toAlbumResponse(album);
                if (sr != null) albumResponses.add(sr);
            }
            response.setAlbums(albumResponses);
        } else {
            response.setAlbums(new HashSet<>());
        }
        return response;
    }
    public Artist uploadArtistFiles(String artistId, MultipartFile image) throws IOException {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        if(image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }
        Map uploadImage = cloudinary.uploader().upload(image.getBytes(),
                ObjectUtils.asMap("folder", "artist_app/images"));

        artist.setImageUrlAr(uploadImage.get("secure_url").toString());

        return artistRepository.save(artist);
    }
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null &&
                authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            String username = authentication.getName();
            return userRepository.findByUsername(username).orElse(null);
        }
        return null;
    }
    private Set<String> getFollowerArtistIdOfCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            return Collections.emptySet();
        }
        return followerRepository.findAllByUser(user).stream()
                .map(follower -> follower.getArtist().getId())
                .collect(Collectors.toSet());
    }
    private boolean isArtistFollowerByCurrentUser(Artist artist) {
        User user = getCurrentUser();
        if(user == null) return false;
        return followerRepository.existsByUserAndArtist(user,artist);
    }

}
