package com.devteria.identityservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.devteria.identityservice.dto.request.SongCreationRequest;
import com.devteria.identityservice.dto.request.SongUpdateRequest;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.*;
import com.devteria.identityservice.enums.SongType;
import com.devteria.identityservice.enums.Status;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.mapper.PagingMapper;
import com.devteria.identityservice.repository.DownloadedSongRepository;
import com.devteria.identityservice.repository.FavoriteRepository;
import com.devteria.identityservice.repository.GenreRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.hibernate.mapping.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Pageable;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

@Service
public class SongService {
    private final SongRepository songRepository;
    private final YearService yearService;
    private final Cloudinary cloudinary;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final DownloadedSongRepository downloadedSongRepository;
    private final GenreRepository genreRepository;
    private final com.devteria.identityservice.repository.UserInteractionRepository userInteractionRepository;
    private final AiSongEnrichmentService aiSongEnrichmentService;

    public SongService(SongRepository songRepository, YearService yearService, Cloudinary cloudinary, FavoriteRepository favoriteRepository, UserRepository userRepository, DownloadedSongRepository downloadedSongRepository, GenreRepository genreRepository, com.devteria.identityservice.repository.UserInteractionRepository userInteractionRepository, AiSongEnrichmentService aiSongEnrichmentService) {
        this.songRepository = songRepository;
        this.yearService = yearService;
        this.cloudinary = cloudinary;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.downloadedSongRepository = downloadedSongRepository;
        this.genreRepository = genreRepository;
        this.userInteractionRepository = userInteractionRepository;
        this.aiSongEnrichmentService = aiSongEnrichmentService;
    }

    @Transactional
    public SongResponse createSong(SongCreationRequest request) {
        Song song = new Song();
        song.setName(request.getName());
        song.setDescription(request.getDescription());
        song.setDuration(request.getDuration());
        song.setReleasedDate(request.getReleasedDate());
        song.setStatus(Status.DRAFT);
        song.setType(SongType.AUDIO);
        Year year = yearService.createYear(request.getReleasedDate().getYear());
        song.setYear(year);
        if (request.getGenreIds() != null) {
            List<Genre> genres = genreRepository.findAllById(request.getGenreIds());
            song.setGenres(new java.util.HashSet<>(genres));
        }
        song = songRepository.save(song);
        return toSongResponse(song);
    }
    public PageResponse<SongResponse> getSongs(int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "releasedDate"));
        Page<Song> songPage = songRepository.findAll(pageable);

        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();

        List<SongResponse> songResponses = songPage.getContent().stream()
                .map(song -> {
                    SongResponse response = toSongResponse(song);
                    response.setFavorite(likedSongIds.contains(song.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        return PagingMapper.toPageResponse(songPage, songResponses);
    }
    public PageResponse<SongResponse> getSongsByGenre(String genreId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "releasedDate"));
        Page<Song> songPage = songRepository.findByGenres_Id(genreId, pageable);

        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();

        List<SongResponse> songResponses = songPage.getContent().stream()
                .map(song -> {
                    SongResponse response = toSongResponse(song);
                    response.setFavorite(likedSongIds.contains(song.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        return PagingMapper.toPageResponse(songPage, songResponses);
    }
    public SongResponse getSong(String id) {
        Song song = songRepository.findById(id)
                .orElseThrow(()-> new AppException(ErrorCode.SONG_NOT_EXISTED));
        SongResponse response = toSongResponse(song);
        response.setFavorite(isSongLikedByCurrentUser(song));
        return response;
    }
    public SongResponse getSongByName(String name) {
        Song song = songRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        SongResponse response = toSongResponse(song);
        response.setFavorite(isSongLikedByCurrentUser(song));
        return response;
    }
    public List<SongResponse> searchSongs(String key) {
        if(key == null || key.trim().isEmpty()) {
            return List.of();
        }
        Pageable limit = PageRequest.of(0,50);
        List<Song> songs = songRepository.findByNameContainingIgnoreCase(key,limit).getContent();
        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();
        return songs.stream()
                .map(song -> {
                    SongResponse response = toSongResponse(song);
                    response.setFavorite(likedSongIds.contains(song.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }
    public PageResponse<SongResponse> searchSongsForAdmin(String key, int page, int size) {
        if (key == null || key.trim().isEmpty()) {
            return new PageResponse<>(page, 0, size, 0, Collections.emptyList());
        }

        Pageable pageable = PageRequest.of(page - 1, size);

        Page<Song> songPage = songRepository.findByNameContainingIgnoreCase(key, pageable);

        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();

        List<SongResponse> songResponses = songPage.getContent().stream()
                .map(song -> {
                    SongResponse response = toSongResponse(song);
                    response.setFavorite(likedSongIds.contains(song.getId()));
                    return response;
                })
                .collect(Collectors.toList());

        return new PageResponse<>(
                page,
                songPage.getTotalPages(),
                songPage.getSize(),
                songPage.getTotalElements(),
                songResponses
        );
    }
    @Transactional
    public void deleteSong(String id) {
        songRepository.findById(id)
                        .orElseThrow(()-> new AppException(ErrorCode.SONG_NOT_EXISTED));
        songRepository.deleteById(id);
    }
    @Transactional
    public SongResponse updateSong(String id, SongUpdateRequest request) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));
        song.setName(request.getName());
        song.setDescription(request.getDescription());
        song.setStatus(request.getStatus());
        song.setDuration(request.getDuration());
        song.setType(request.getType());
        if(request.getReleasedDate() != null) {
            song.setReleasedDate(request.getReleasedDate());
            Year year = yearService.createYear(request.getReleasedDate().getYear());
            song.setYear(year);
        }
        if (request.getGenreIds() != null) {
            List<Genre> genres = genreRepository.findAllById(request.getGenreIds());
            song.setGenres(new java.util.HashSet<>(genres));
        }
        Song updateSong = songRepository.save(song);
        return toSongResponse(updateSong);
    }
    @Transactional
    public Song uploadSongFile(String songId, MultipartFile image,MultipartFile audio) throws IOException {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));
        if (image == null || image.isEmpty() || audio == null || audio.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_KEY);
        }

        Map uploadImage = cloudinary.uploader().upload(image.getBytes(),
                ObjectUtils.asMap("folder", "music_app/images"));
        Map uploadAudio = cloudinary.uploader().upload(audio.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "video", // Cloudinary yĂªu cáº§u kiá»ƒu nĂ y cho file audio/mp3
                        "folder", "music_app/audios"
                ));
        song.setImageUrl(uploadImage.get("secure_url").toString());
        song.setAudioUrl(uploadAudio.get("secure_url").toString());
        song.setStatus(Status.PUBLISHED);

        Song savedSong = songRepository.save(song);
        aiSongEnrichmentService.enrichSongMetadataAsync(songId);
        return savedSong;

    }
    @Transactional
    public void incrementPlayCount(String songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        Long currentCount = song.getPlayCount() == null ? 0L : song.getPlayCount();
        song.setPlayCount(currentCount + 1);

        songRepository.save(song);
    }
    public List<SongResponse> getTopSongs() {
        List<Song> topSongs = songRepository.findTop10ByOrderByPlayCountDesc();
        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();

        return topSongs.stream()
                .map(song -> {
                    SongResponse response = toSongResponse(song);
                    response.setFavorite(likedSongIds.contains(song.getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }
    // TiĂªm DownloadedSongRepository vĂ o Constructor
    public boolean isSongDownloadedByCurrentUser(String songId) {
        User user = getCurrentUser();
        if (user == null) return false;

        Song song = songRepository.findById(songId).orElse(null);
        if (song == null) return false;

        return downloadedSongRepository.existsByUserAndSong(user, song);
    }
    // Cáº­p nháº­t hĂ m downloadSong cÅ©
    @Transactional
    public ResponseEntity<Resource> downloadSong(String id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        User currentUser = getCurrentUser();
        if (currentUser != null) {
            // KIá»‚M TRA: Náº¿u chÆ°a táº£i thĂ¬ má»›i lÆ°u vĂ o DB
            if (!downloadedSongRepository.existsByUserAndSong(currentUser, song)) {
                DownloadedSong download = new DownloadedSong();
                download.setUser(currentUser);
                download.setSong(song);
                download.setDownloadedAt(LocalDateTime.now());
                downloadedSongRepository.save(download);
            }
            // Náº¿u Ä‘Ă£ táº£i rá»“i thĂ¬ bá» qua bÆ°á»›c save, chá»‰ tiáº¿n hĂ nh tráº£ vá» file nháº¡c bĂªn dÆ°á»›i
        }

        try {
            Resource resource = new UrlResource(song.getAudioUrl());
            ContentDisposition contentDisposition = ContentDisposition.attachment()
                    .filename(song.getName() + ".mp3", StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                    .contentType(MediaType.parseMediaType("audio/mpeg"))
                    .body(resource);
        } catch (MalformedURLException e) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
    }

    // HĂ m láº¥y danh sĂ¡ch bĂ i hĂ¡t Ä‘Ă£ táº£i
    public List<SongResponse> getDownloadedSongs() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();

        List<DownloadedSong> downloads = downloadedSongRepository.findAllByUserOrderByDownloadedAtDesc(user);

        return downloads.stream()
                .map(download -> {
                    SongResponse response = toSongResponse(download.getSong());
                    response.setFavorite(isSongLikedByCurrentUser(download.getSong()));
                    return response;
                })
                .collect(Collectors.toList());
    }

    public List<SongResponse> getRecentlyPlayedSongs() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();

        Pageable limit = PageRequest.of(0, 20);
        Page<com.devteria.identityservice.entity.UserInteraction> interactions = 
            userInteractionRepository.findByUserAndInteractionTypeOrderByUpdatedAtDesc(user, "LISTEN", limit);

        Set<String> likedSongIds = getLikedSongIdsOfCurrentUser();

        return interactions.getContent().stream()
                .map(interaction -> {
                    SongResponse response = toSongResponse(interaction.getSong());
                    response.setFavorite(likedSongIds.contains(interaction.getSong().getId()));
                    return response;
                })
                .collect(Collectors.toList());
    }
    public static SongResponse toSongResponse(Song song) {
        if (song == null) return null;
        SongResponse response = new SongResponse();
        response.setId(song.getId());
        response.setName(song.getName());
        response.setDescription(song.getDescription());
        response.setStatus(song.getStatus());
        response.setDuration(song.getDuration());
        response.setReleasedDate(song.getReleasedDate());
        response.setType(song.getType());
        response.setImageUrl(song.getImageUrl());
        response.setAudioUrl(song.getAudioUrl());
        response.setFavorite(false);
        response.setPlayCount(song.getPlayCount() != null ? song.getPlayCount() : 0L);
        if (song.getArtists() != null && !song.getArtists().isEmpty()) {
            // CĂ¡ch 1: Sá»­ dá»¥ng Stream API (Hiá»‡n Ä‘áº¡i, an toĂ n)
            Artist firstArtist = song.getArtists().stream().findFirst().orElse(null);

            if (firstArtist != null) {
                response.setArtistName(firstArtist.getName());
            }

            // CĂ¡ch 2 (Truyá»n thá»‘ng - DĂ¹ng Iterator):
            // response.setArtistName(song.getArtists().iterator().next().getName());
        }
        if (song.getGenres() != null && !song.getGenres().isEmpty()) {
            response.setGenres(song.getGenres().stream()
                    .map(GenreService::toGenreResponse)
                    .collect(Collectors.toList()));
        }
        return response;
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
    private Set<String> getLikedSongIdsOfCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            return Collections.emptySet();
        }
        return favoriteRepository.findAllByUser(user).stream()
                .map(favorite -> favorite.getSong().getId())
                .collect(Collectors.toSet());
    }
    private boolean isSongLikedByCurrentUser(Song song) {
        User user = getCurrentUser();
        if (user == null) return false;
        return favoriteRepository.existsByUserAndSong(user, song);
    }

    public SongResponse enrichSongMetadata(String songId, boolean overwrite) {
        return aiSongEnrichmentService.enrichSongMetadata(songId, overwrite);
    }
}
