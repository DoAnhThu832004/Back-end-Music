package com.devteria.identityservice.controller;

import com.cloudinary.Api;
import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.GenreCreationRequest;
import com.devteria.identityservice.dto.request.GenreUpdateRequest;
import com.devteria.identityservice.dto.response.GenreResponse;
import com.devteria.identityservice.service.GenreService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import com.devteria.identityservice.dto.response.AutoPlaylistResponse;
import com.devteria.identityservice.dto.response.GenrePlayCountResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/genres")
public class GenreController {
    private final GenreService genreService;

    public GenreController(GenreService genreService) {
        this.genreService = genreService;
    }
    @PostMapping
    public ApiResponse<GenreResponse> createGenre(@RequestBody @Valid GenreCreationRequest request) {
        return ApiResponse.<GenreResponse>builder()
                .result(genreService.createGenre(request))
                .build();
    }
    @GetMapping
    public ApiResponse<List<GenreResponse>> getGenres() {
        return ApiResponse.<List<GenreResponse>>builder()
                .result(genreService.getGenres())
                .build();
    }
    @GetMapping("/{genreId}")
    public ApiResponse<GenreResponse> getGenre(@PathVariable("genreId") String id) {
        return ApiResponse.<GenreResponse>builder()
                .result(genreService.getGenre(id))
                .build();
    }
    @GetMapping("/searchName")
    public ApiResponse<GenreResponse> getGenreByName(@RequestParam String name) {
        return ApiResponse.<GenreResponse>builder()
                .result(genreService.getGenreByName(name))
                .build();
    }
    @GetMapping("/searchKeyG")
    public ApiResponse<GenreResponse> getGenreByKeyG(@RequestParam String keyG) {
        return ApiResponse.<GenreResponse>builder()
                .result(genreService.getGenreByKeyG(keyG))
                .build();
    }
    @PutMapping("/{genreId}")
    public ApiResponse<GenreResponse> updateGenre(@PathVariable("genreId") String id, @RequestBody @Valid GenreUpdateRequest request) {
        return ApiResponse.<GenreResponse>builder()
                .result(genreService.updateGenre(id,request))
                .build();
    }
    @DeleteMapping("/{genreId}")
    public ApiResponse<String> deleteGenre(@PathVariable("genreId") String id) {
        genreService.deleteGenre(id);
        return ApiResponse.<String>builder()
                .result("Genre has been deleted")
                .build();
    }
    
    @GetMapping("/trending")
    public ApiResponse<List<GenrePlayCountResponse>> getTrendingGenres() {
        return ApiResponse.<List<GenrePlayCountResponse>>builder()
                .result(genreService.getTrendingGenres())
                .build();
    }

    @GetMapping("/auto-playlists")
    public ApiResponse<List<AutoPlaylistResponse>> getAutoPlaylists() {
        return ApiResponse.<List<AutoPlaylistResponse>>builder()
                .result(genreService.getAutoPlaylists())
                .build();
    }
}
