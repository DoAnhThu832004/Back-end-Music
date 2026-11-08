package com.devteria.identityservice.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.devteria.identityservice.dto.request.GenreUpdateRequest;
import com.devteria.identityservice.dto.request.PlaylistCreationRequest;
import com.devteria.identityservice.dto.request.PlaylistUpdateRequest;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.PlaylistResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.*;
import com.devteria.identityservice.enums.Status;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.PlaylistRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class PlaylistService {
    private final PlaylistRepository playlistRepository;
    private final SongRepository songRepository;
    private final Cloudinary cloudinary;
    private final UserRepository userRepository;

    public PlaylistService(PlaylistRepository playlistRepository, SongRepository songRepository,Cloudinary cloudinary,UserRepository userRepository) {
        this.playlistRepository = playlistRepository;
        this.songRepository = songRepository;
        this.cloudinary = cloudinary;
        this.userRepository = userRepository;
    }
    @Transactional
    public PlaylistResponse createPlaylist(PlaylistCreationRequest request) {
        Playlist playlist = toPlaylist(request);
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(name)
                .orElseThrow(()-> new AppException(ErrorCode.USER_NOT_EXISTED));
        playlist.setUser(user);
        return toPlaylistResponse(playlistRepository.save(playlist));

    }
    public List<PlaylistResponse> getPlayLists() {
        return playlistRepository.findAllAdminPlaylists().stream()
                .map(PlaylistService::toPlaylistResponse)
                .toList();
    }
    public List<PlaylistResponse> getMyPlaylists() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        return playlistRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(PlaylistService::toPlaylistResponse).toList();
    }

    public PageResponse<PlaylistResponse> getPlaylistsByGenre(String genreId, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Playlist> playlistPage = playlistRepository.findAdminPlaylistsByGenreId(genreId, pageable);
        List<PlaylistResponse> responses = playlistPage.getContent().stream()
                .map(PlaylistService::toPlaylistResponse)
                .toList();
        return new PageResponse<>(
                page,
                playlistPage.getTotalPages(),
                playlistPage.getSize(),
                playlistPage.getTotalElements(),
                responses
        );
    }
    public PageResponse<PlaylistResponse> searchPlaylistsForAdmin(String key, int page, int size) {
        if (key == null || key.trim().isEmpty()) {
            return new PageResponse<>(page, 0, size, 0, java.util.Collections.emptyList());
        }

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Playlist> playlistPage = playlistRepository.findByTitleContainingIgnoreCase(key, pageable);
        List<PlaylistResponse> responses = playlistPage.getContent().stream()
                .map(PlaylistService::toPlaylistResponse)
                .toList();

        return new PageResponse<>(
                page,
                playlistPage.getTotalPages(),
                playlistPage.getSize(),
                playlistPage.getTotalElements(),
                responses
        );
    }
    public PlaylistResponse getPlaylist(String id) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PLAYLIST_NOT_EXISTED));
        return toPlaylistResponse(playlist);
    }
    @Transactional
    public PlaylistResponse updatePlaylist(String id, PlaylistUpdateRequest request) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(()-> new AppException(ErrorCode.PLAYLIST_NOT_EXISTED));
        mapRequestToPlaylist(playlist,request);
        return toPlaylistResponse(playlistRepository.save(playlist));
    }
    @Transactional
    public void deletePlaylist(String id) {
        playlistRepository.deleteById(id);
    }
    private void mapRequestToPlaylist(Playlist playlist, PlaylistUpdateRequest request) {
        if(request == null) return;
        if(request.getTitle() != null && !request.getTitle().isEmpty()) {
            playlist.setTitle(request.getTitle());
        }
        if(request.getDescription() != null && !request.getDescription().isEmpty()) {
            playlist.setDescription(request.getDescription());
        }
    }
    public Playlist uploadPlaylistFiles(String playlistId, MultipartFile image) throws IOException {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAYLIST_NOT_EXISTED));
        Map uploadImage = cloudinary.uploader().upload(image.getBytes(),
                ObjectUtils.asMap("folder", "playlist_app/images"));

        playlist.setImageUrlP(uploadImage.get("secure_url").toString());

        return playlistRepository.save(playlist);
    }
    @Transactional
    public PlaylistResponse addSongToPlaylist(String playlistId, String songId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAYLIST_NOT_EXISTED));

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED)); // Đảm bảo bạn có ErrorCode.SONG_NOT_EXISTED

        playlist.getSongPlayList().add(song);
        return toPlaylistResponse(playlistRepository.save(playlist));
    }
    @Transactional
    public PlaylistResponse removeSongFromPlaylist(String playlistId, String songId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new AppException(ErrorCode.PLAYLIST_NOT_EXISTED));

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        playlist.getSongPlayList().remove(song);
        return toPlaylistResponse(playlistRepository.save(playlist));
    }
    // Cần import PageResponse, SongResponse, Pageable, PageRequest...
    public PageResponse<SongResponse> getSongsInPlaylist(String playlistId, int page, int size) {
        // Sắp xếp theo tên bài hát mặc định
        Sort sort = Sort.by("name").ascending();
        Pageable pageable = PageRequest.of(page - 1, size, sort);

        Page<Song> pageData = songRepository.findByPlaylists_Id(playlistId, pageable);

        // Chuyển đổi danh sách Entity sang DTO thủ công (giả định dùng SongService.toSongResponse)
        List<SongResponse> songResponses = pageData.getContent().stream()
                .map(SongService::toSongResponse)
                .toList();

        // Khởi tạo PageResponse bằng Constructor thay vì Builder
        return new PageResponse<>(
                page,
                pageData.getTotalPages(),
                pageData.getSize(),
                pageData.getTotalElements(),
                songResponses
        );
    }
    public Playlist toPlaylist(PlaylistCreationRequest request) {
        if(request == null) return null;
        Playlist playlist = new Playlist();
        playlist.setTitle(request.getTitle());
        playlist.setDescription(request.getDescription());
        return playlist;
    }
    public static PlaylistResponse toPlaylistResponse(Playlist playlist) {
        if(playlist == null) return null;
        PlaylistResponse response = new PlaylistResponse();
        response.setId(playlist.getId());
        response.setTitle(playlist.getTitle());
        response.setDescription(playlist.getDescription());
        response.setImageUrlP(playlist.getImageUrlP());
        return response;
    }
}
