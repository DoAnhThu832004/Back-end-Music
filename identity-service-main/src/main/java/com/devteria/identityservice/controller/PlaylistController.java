package com.devteria.identityservice.controller;

import com.cloudinary.Api;
import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.PlaylistCreationRequest;
import com.devteria.identityservice.dto.request.PlaylistUpdateRequest;
import com.devteria.identityservice.dto.response.ArtistResponse;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.PlaylistResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Playlist;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.service.ArtistService;
import com.devteria.identityservice.service.PlaylistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/playlists")
public class PlaylistController {
    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }
    @PostMapping()
    public ApiResponse<PlaylistResponse> createPlaylist(@RequestBody @Valid PlaylistCreationRequest request) {
        return ApiResponse.<PlaylistResponse>builder()
                .result(playlistService.createPlaylist(request))
                .build();
    }
    @GetMapping
    public ApiResponse<List<PlaylistResponse>> getPlaylists() {
        return ApiResponse.<List<PlaylistResponse>>builder()
                .result(playlistService.getPlayLists())
                .build();
    }
    @GetMapping("/admin/searchKey")
    public ApiResponse<PageResponse<PlaylistResponse>> searchPlaylistsForAdmin(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.<PageResponse<PlaylistResponse>>builder()
                .result(playlistService.searchPlaylistsForAdmin(name, page, size))
                .build();
    }
    @GetMapping("/myList")
    public ApiResponse<List<PlaylistResponse>> getMyPlaylists() {
        return ApiResponse.<List<PlaylistResponse>>builder()
                .result(playlistService.getMyPlaylists())
                .build();
    }
    @GetMapping("/{playlistId}")
    public ApiResponse<PlaylistResponse> getPlaylist(@PathVariable("playlistId") String id) {
        return ApiResponse.<PlaylistResponse>builder()
                .result(playlistService.getPlaylist(id))
                .build();
    }
    @PutMapping("/{playlistId}")
    public ApiResponse<PlaylistResponse> updatePlaylist(@PathVariable("playlistId") String id, @RequestBody @Valid PlaylistUpdateRequest request) {
        return ApiResponse.<PlaylistResponse>builder()
                .result(playlistService.updatePlaylist(id,request))
                .build();
    }
    @DeleteMapping("/{playlistId}")
    public ApiResponse<String> deletePlaylist(@PathVariable("playlistId") String id) {
        playlistService.deletePlaylist(id);
        return ApiResponse.<String>builder()
                .result("Playlist updated successfully")
                .build();
    }
    @PostMapping("/{playlistId}/songs/{songId}")
    public ApiResponse<PlaylistResponse> addSongToPlaylist(
            @PathVariable String playlistId,
            @PathVariable String songId) {
        return ApiResponse.<PlaylistResponse>builder()
                .result(playlistService.addSongToPlaylist(playlistId, songId))
                .build();
    }

    @DeleteMapping("/{playlistId}/songs/{songId}")
    public ApiResponse<PlaylistResponse> removeSongFromPlaylist(
            @PathVariable String playlistId,
            @PathVariable String songId) {
        return ApiResponse.<PlaylistResponse>builder()
                .result(playlistService.removeSongFromPlaylist(playlistId, songId))
                .build();
    }
    @GetMapping("/{playlistId}/songs")
    public ApiResponse<PageResponse<SongResponse>> getSongs(
            @PathVariable String playlistId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.<PageResponse<SongResponse>>builder()
                .result(playlistService.getSongsInPlaylist(playlistId, page, size))
                .build();
    }
    @PostMapping("/{playlistId}/upload")
    ApiResponse<PlaylistResponse> uploadPlaylistFiles(
            @PathVariable("playlistId") String id,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            Playlist updated = playlistService.uploadPlaylistFiles(id,image);
            return ApiResponse.<PlaylistResponse>builder()
                    .result(PlaylistService.toPlaylistResponse(updated))
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }

    @GetMapping("/genre/{genreId}")
    public ApiResponse<PageResponse<PlaylistResponse>> getPlaylistsByGenre(
            @PathVariable("genreId") String genreId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<PlaylistResponse>>builder()
                .result(playlistService.getPlaylistsByGenre(genreId, page, size))
                .build();
    }
}
