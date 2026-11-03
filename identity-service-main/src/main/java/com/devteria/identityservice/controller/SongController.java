package com.devteria.identityservice.controller;

import com.cloudinary.Api;
import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.SongCreationRequest;
import com.devteria.identityservice.dto.request.SongUpdateRequest;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.service.SongService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/songs")
public class SongController {
    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @PostMapping
    public ApiResponse<SongResponse> createSong(@RequestBody @Valid SongCreationRequest request) {
        return ApiResponse.<SongResponse>builder()
                .result(songService.createSong(request))
                .build();
    }
    @GetMapping
    public ApiResponse<PageResponse<SongResponse>> getSongs(
            @RequestParam(value = "genreId", required = false) String genreId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        if (genreId != null && !genreId.isEmpty()) {
            return ApiResponse.<PageResponse<SongResponse>>builder()
                    .result(songService.getSongsByGenre(genreId, page, size))
                    .build();
        }
        return ApiResponse.<PageResponse<SongResponse>>builder()
                .result(songService.getSongs(page, size))
                .build();
    }
    @GetMapping("/{songId}")
    public ApiResponse<SongResponse> getSong(@PathVariable("songId") String id) {
        return ApiResponse.<SongResponse>builder()
                .result(songService.getSong(id))
                .build();
    }
    @GetMapping("/search")
    public ApiResponse<SongResponse> getSongByName(@RequestParam String name) {
        return ApiResponse.<SongResponse>builder()
                .result(songService.getSongByName(name))
                .build();
    }
    @GetMapping("/searchKey")
    public ApiResponse<List<SongResponse>> searchSongs(@RequestParam String name) {
        return ApiResponse.<List<SongResponse>>builder()
                .result(songService.searchSongs(name))
                .build();
    }
    @GetMapping("/admin/searchKey")
    public ApiResponse<PageResponse<SongResponse>> searchSongsForAdmin(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.<PageResponse<SongResponse>>builder()
                .result(songService.searchSongsForAdmin(name, page, size))
                .build();
    }
    @PutMapping("/{songId}")
    public ApiResponse<SongResponse> updateSong(@PathVariable("songId") String id,@RequestBody @Valid SongUpdateRequest request) {
        return ApiResponse.<SongResponse>builder()
                .result(songService.updateSong(id,request))
                .build();
    }
    @DeleteMapping("/{songId}")
    public ApiResponse<String> deleteSong(@PathVariable("songId") String id) {
        songService.deleteSong(id);
        return ApiResponse.<String>builder()
                .result("Song has been deleted")
                .build();
    }
    @PostMapping("/{songId}/upload")
    public ApiResponse<SongResponse> uploadSongFiles(
            @PathVariable("songId") String id,
            @RequestParam("image") MultipartFile image,
            @RequestParam("audio") MultipartFile audio) {
        try {
            Song updated = songService.uploadSongFile(id, image, audio);
            return ApiResponse.<SongResponse>builder()
                    .result(SongService.toSongResponse(updated))
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }
    // Endpoint ghi nháº­n lÆ°á»£t nghe, gá»i khi ngÆ°á»i dĂ¹ng báº¯t Ä‘áº§u phĂ¡t nháº¡c
    @PostMapping("/{songId}/play")
    public ApiResponse<String> incrementPlayCount(@PathVariable("songId") String id) {
        songService.incrementPlayCount(id);
        return ApiResponse.<String>builder()
                .result("LÆ°á»£t nghe Ä‘Ă£ Ä‘Æ°á»£c cáº­p nháº­t thĂ nh cĂ´ng")
                .build();
    }

    @GetMapping("/top-charts")
    public ApiResponse<List<SongResponse>> getTopSongs() {
        return ApiResponse.<List<SongResponse>>builder()
                .result(songService.getTopSongs())
                .build();
    }
    // Trong SongController.java
    @GetMapping("/{songId}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadSong(@PathVariable("songId") String id) {
        return songService.downloadSong(id);
    }
    @GetMapping("/downloaded")
    public ApiResponse<List<SongResponse>> getDownloadedSongs() {
        return ApiResponse.<List<SongResponse>>builder()
                .result(songService.getDownloadedSongs())
                .build();
    }
    @GetMapping("/{songId}/check-downloaded")
    public ApiResponse<Boolean> checkDownloaded(@PathVariable("songId") String id) {
        return ApiResponse.<Boolean>builder()
                .result(songService.isSongDownloadedByCurrentUser(id))
                .build();
    }
    @GetMapping("/recently-played")
    public ApiResponse<List<SongResponse>> getRecentlyPlayedSongs() {
        return ApiResponse.<List<SongResponse>>builder()
                .result(songService.getRecentlyPlayedSongs())
                .build();
    }

    /**
     * [ADMIN] Kích hoạt AI phân tích và làm giàu mô tả, thể loại bài hát.
     * POST /songs/{id}/ai-enrich
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/ai-enrich")
    public ApiResponse<SongResponse> enrichSongMetadata(
            @PathVariable("id") String songId,
            @RequestParam(value = "overwrite", defaultValue = "false") boolean overwrite) {
        return ApiResponse.<SongResponse>builder()
                .result(songService.enrichSongMetadata(songId, overwrite))
                .message("AI metadata enrichment completed")
                .build();
    }
}
