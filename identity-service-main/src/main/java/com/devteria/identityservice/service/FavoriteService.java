package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Favorite;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.FavoriteRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    public FavoriteService(FavoriteRepository favoriteRepository, SongRepository songRepository, UserRepository userRepository) {
        this.favoriteRepository = favoriteRepository;
        this.songRepository = songRepository;
        this.userRepository = userRepository;
    }
    @Transactional
    public void addSongToFavorite(String songId) {
        User user = getCurrentUser();
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));
        if(favoriteRepository.findByUserAndSong(user,song).isEmpty()) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setSong(song);
            favoriteRepository.save(favorite);
        }
    }
    @Transactional
    public void removeSongFromFavorite(String songId) {
        User user = getCurrentUser();
        Song song = songRepository.findById(songId)
                .orElseThrow(()-> new AppException(ErrorCode.SONG_NOT_EXISTED));
        favoriteRepository.findByUserAndSong(user, song)
                .ifPresent(fav -> {
                    favoriteRepository.delete(fav);
                });
    }
    public List<SongResponse> getMyFavorites() {
        User user = getCurrentUser();

        List<Favorite> favorites = favoriteRepository.findAllByUser(user);

        // Sử dụng Stream API để chuyển đổi gọn gàng
        return favorites.stream()
                .map(favorite -> {
                    Song song = favorite.getSong();
                    // Tái sử dụng static method từ SongService để không lặp code logic mapping
                    SongResponse response = SongService.toSongResponse(song);

                    // Vì đây là danh sách yêu thích, chắc chắn isFavorite = true
                    if (response != null) {
                        response.setFavorite(true);
                    }
                    return response;
                })
                .collect(Collectors.toList());
    }
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(()-> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
}
