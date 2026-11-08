package com.devteria.identityservice.controller;

import com.cloudinary.Api;
import com.devteria.identityservice.dto.request.AlbumCreationRequest;
import com.devteria.identityservice.dto.request.AlbumUpdateRequest;
import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.response.AlbumResponse;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Album;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.service.AlbumService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/albums")
public class AlbumController {
    private final AlbumService albumService;

    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }
    @PostMapping
    ApiResponse<AlbumResponse> createAlbum(@RequestBody @Valid AlbumCreationRequest request) {
        return ApiResponse.<AlbumResponse>builder()
                .result(albumService.createAlbum(request))
                .build();
    }
    @GetMapping
    public ApiResponse<PageResponse<AlbumResponse>> getAlbums(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<AlbumResponse>>builder()
                .result(albumService.getAlbums(page, size))
                .build();
    }
    @GetMapping("/{albumId}")
    ApiResponse<AlbumResponse> getAlbum(@PathVariable("albumId") String id) {
        return ApiResponse.<AlbumResponse>builder()
                .result(albumService.getAlbum(id))
                .build();
    }
    @GetMapping("/searchKey")
    public ApiResponse<PageResponse<AlbumResponse>> searchAlbums(
            @RequestParam String name,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<AlbumResponse>>builder()
                .result(albumService.searchAlbums(name, page, size))
                .build();
    }
    @GetMapping("/admin/searchKey")
    public ApiResponse<PageResponse<AlbumResponse>> searchAlbumsForAdmin(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.<PageResponse<AlbumResponse>>builder()
                .result(albumService.searchAlbumsForAdmin(name, page, size))
                .build();
    }
    @PutMapping("/{albumId}")
    ApiResponse<AlbumResponse> updateAlbum(@PathVariable("albumId") String id,@RequestBody @Valid AlbumUpdateRequest request) {
        return ApiResponse.<AlbumResponse>builder()
                .result(albumService.updateAlbum(id,request))
                .build();
    }
    @DeleteMapping("/{albumId}")
    ApiResponse<String> deleteAlbum(@PathVariable("albumId") String id) {
        albumService.deleteAlbum(id);
        return ApiResponse.<String>builder()
                .result("Album has been deleted")
                .build();
    }
    @PostMapping("/{albumId}/upload")
    ApiResponse<AlbumResponse> uploadAlbumFiles(
            @PathVariable("albumId") String id,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            Album updated = albumService.uploadAlbumFiles(id,image);
            return ApiResponse.<AlbumResponse>builder()
                    .result(AlbumService.toAlbumResponse(updated))
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }
    @PutMapping("/{albumId}/songs/{songId}")
    public ApiResponse<String> addSongToAlbum(@PathVariable String albumId, @PathVariable String songId) {
        albumService.addSongToAlbum(albumId, songId);
        return ApiResponse.<String>builder()
                .result("Song added to album")
                .build();
    }
    @DeleteMapping("/{albumId}/songs/{songId}")
    public ApiResponse<String> removeSongFromAlbum(@PathVariable String albumId, @PathVariable String songId) {
        albumService.removeSongToAlbum(albumId, songId);
        return ApiResponse.<String>builder()
                .result("Song removed from album")
                .build();
    }
    @PutMapping("/{albumId}/artists/{artistId}")
    public ApiResponse<String> addArtistToAlbum(@PathVariable("albumId") String albumId, @PathVariable("artistId") String artistId) {
        albumService.addArtistToAlbum(albumId,artistId);
        return ApiResponse.<String>builder()
                .result("Artist added from album")
                .build();
    }
    @DeleteMapping("/{albumId}/artists/{artistId}")
    public ApiResponse<String> removeArtistToAlbum(@PathVariable("albumId") String albumId, @PathVariable("artistId") String artistId) {
        albumService.removeArtistToAlbum(albumId,artistId);
        return ApiResponse.<String>builder()
                .result("Artist removed from album")
                .build();
    }

    @GetMapping("/genre/{genreId}")
    public ApiResponse<PageResponse<AlbumResponse>> getAlbumsByGenre(
            @PathVariable("genreId") String genreId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<AlbumResponse>>builder()
                .result(albumService.getAlbumsByGenre(genreId, page, size))
                .build();
    }
}
