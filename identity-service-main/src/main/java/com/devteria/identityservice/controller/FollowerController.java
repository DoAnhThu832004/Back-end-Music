package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.response.ArtistResponse; // Import ArtistResponse nếu bạn làm chức năng lấy danh sách
import com.devteria.identityservice.service.FollowerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/followers")
public class FollowerController {

    private final FollowerService followerService;

    // Constructor Injection (thay vì @RequiredArgsConstructor để đồng bộ với phong cách code hiện tại của bạn)
    public FollowerController(FollowerService followerService) {
        this.followerService = followerService;
    }

    @PostMapping("/{artistId}")
    public ApiResponse<String> followArtist(@PathVariable String artistId) {
        followerService.followArtist(artistId);

        ApiResponse<String> response = new ApiResponse<>();
        response.setResult("Followed artist successfully");
        return response;
    }

    @DeleteMapping("/{artistId}")
    public ApiResponse<String> unfollowArtist(@PathVariable String artistId) {
        followerService.unfollowArtist(artistId);

        ApiResponse<String> response = new ApiResponse<>();
        response.setResult("Unfollowed artist successfully");
        return response;
    }
    @GetMapping("/artists/{artistId}/count")
    public ApiResponse<Map<String, Long>> countFollowersOfArtist(@PathVariable String artistId) {
        long count = followerService.countFollowersOfArtist(artistId);

        ApiResponse<Map<String, Long>> response = new ApiResponse<>();
        response.setResult(Map.of("count", count));
        return response;
    }

    @GetMapping("/my-artists")
    public ApiResponse<List<ArtistResponse>> getFollowedArtists() {
        List<ArtistResponse> followedArtists = followerService.getFollowedArtists();

        ApiResponse<List<ArtistResponse>> response = new ApiResponse<>();
        response.setResult(followedArtists);
        // Có thể thêm message nếu muốn: response.setMessage("Lấy danh sách nghệ sĩ theo dõi thành công");

        return response;
    }
}
