package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.ArtistCreationRequest;
import com.devteria.identityservice.dto.request.ArtistUpdateRequest;
import com.devteria.identityservice.dto.response.AlbumResponse;
import com.devteria.identityservice.dto.response.ArtistResponse;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.service.ArtistService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/artists")
public class ArtistController {
    private final ArtistService artistService;

    public ArtistController(ArtistService artistService) {
        this.artistService = artistService;
    }

    @PostMapping()
    ApiResponse<ArtistResponse> createArtist(@RequestBody @Valid ArtistCreationRequest request) {
        return ApiResponse.<ArtistResponse>builder()
                .result(artistService.createArtist(request))
                .build();
    }
    @GetMapping()
    ApiResponse<List<ArtistResponse>> getArtists() {
        return ApiResponse.<List<ArtistResponse>>builder()
                .result(artistService.getArtists())
                .build();
    }
    @GetMapping("/{artistId}")
    ApiResponse<ArtistResponse> getArtists(@PathVariable("artistId") String id) {
        return ApiResponse.<ArtistResponse>builder()
                .result(artistService.getArtist(id))
                .build();
    }
    @GetMapping("/search")
    ApiResponse<ArtistResponse> getArtistsName(@RequestParam String name) {
        return ApiResponse.<ArtistResponse>builder()
                .result(artistService.getArtistName(name))
                .build();
    }
    @PutMapping("/{artistId}")
    ApiResponse<ArtistResponse> updateArtist(@PathVariable("artistId") String id, @RequestBody @Valid ArtistUpdateRequest request) {
        return ApiResponse.<ArtistResponse>builder()
                .result(artistService.updateArtist(id,request))
                .build();
    }
    @DeleteMapping("/{artistId}")
    ApiResponse<String> getArtist(@PathVariable("artistId") String id) {
        artistService.deleteArtist(id);
        return ApiResponse.<String>builder()
                .result("Artist has been deleted")
                .build();
    }
    @GetMapping("/searchKey")
    public ApiResponse<List<ArtistResponse>> searchSongs(@RequestParam String name) {
        return ApiResponse.<List<ArtistResponse>>builder()
                .result(artistService.searchArtist(name))
                .build();
    }
    @GetMapping("/admin/searchKey")
    public ApiResponse<PageResponse<ArtistResponse>> searchArtistsForAdmin(
            @RequestParam String name,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.<PageResponse<ArtistResponse>>builder()
                .result(artistService.searchArtistsForAdmin(name, page, size))
                .build();
    }
    @PostMapping("/{artistId}/upload")
    ApiResponse<ArtistResponse> uploadArtistFiles(
            @PathVariable("artistId") String id,
            @RequestParam("image") MultipartFile image
    ) {
        try {
            Artist updated = artistService.uploadArtistFiles(id,image);
            return ApiResponse.<ArtistResponse>builder()
                    .result(ArtistService.toArtistResponse(updated))
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        }
    }
    @PutMapping("/{artistId}/songs/{songId}")
    ApiResponse<String> addArtistToSong(@PathVariable("artistId") String artistId, @PathVariable("songId") String songId) {
        artistService.addArtistToSong(artistId,songId);
        return ApiResponse.<String>builder()
                .result("Song added to artist")
                .build();
    }
    @DeleteMapping("/{artistId}/songs/{songId}")
    ApiResponse<String> removeSongFromArtist(@PathVariable("artistId") String artistId, @PathVariable("songId") String songId) {
        artistService.removeSongToArtist(artistId,songId);
        return ApiResponse.<String>builder()
                .result("Song removed to artist")
                .build();
    }
    @PutMapping("/{artistId}/albums/{albumId}")
    ApiResponse<String> addAlbumToArtist(@PathVariable("artistId") String artistId, @PathVariable("albumId") String albumId) {
        artistService.addAlbumToArtist(artistId,albumId);
        return ApiResponse.<String>builder()
                .result("album added to artist")
                .build();
    }
    @DeleteMapping("/{artistId}/albums/{albumId}")
    ApiResponse<String> removeAlbumFromArtist(@PathVariable("artistId") String artistId, @PathVariable("albumId") String albumId) {
        artistService.removeAlbumToArtist(artistId,albumId);
        return ApiResponse.<String>builder()
                .result("Song removed to artist")
                .build();
    }
}
