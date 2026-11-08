package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.ArtistResponse;
import com.devteria.identityservice.entity.*;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ArtistRepository;
import com.devteria.identityservice.repository.FollowerRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FollowerService {
    private final FollowerRepository followerRepository;
    private final ArtistRepository artistRepository;
    private final UserRepository userRepository;
    private final ArtistService artistService;

    public FollowerService(FollowerRepository followerRepository, ArtistRepository artistRepository, UserRepository userRepository, ArtistService artistService) {
        this.followerRepository = followerRepository;
        this.artistRepository = artistRepository;
        this.userRepository = userRepository;
        this.artistService = artistService;
    }

    @Transactional
    public void followArtist(String artistId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED)); // Nhớ thêm ErrorCode này nếu chưa có

        if (followerRepository.findByUserAndArtist(user,artist).isEmpty()) {
            Follower follower = new Follower(user, artist);
            followerRepository.save(follower);
        }
    }
    @Transactional
    public void unfollowArtist(String artistId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));

        followerRepository.findByUserAndArtist(user, artist)
                .ifPresent(followerRepository::delete);
    }
    public long countFollowersOfArtist(String artistId) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new AppException(ErrorCode.ARTIST_NOT_EXISTED));
        return followerRepository.countByArtist(artist);
    }

    public List<ArtistResponse> getFollowedArtists() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        List<Follower> followers = followerRepository.findByUser(user);

        return followers.stream().map(follower -> {
            Artist artist = follower.getArtist();

            // Tái sử dụng hàm chuyển đổi từ ArtistService (tên hàm tùy thuộc vào code của bạn)
            ArtistResponse response = artistService.toArtistResponse(artist);
            response.setFollowed(true); // Ghi đè lại trạng thái followed vì chắc chắn user đang follow

            return response;
        }).collect(Collectors.toList());
    }
}
