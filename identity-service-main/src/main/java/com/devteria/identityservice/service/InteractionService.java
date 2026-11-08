package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.entity.UserInteraction;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserInteractionRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InteractionService {

    private final UserInteractionRepository userInteractionRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;

    public InteractionService(UserInteractionRepository userInteractionRepository,
                              UserRepository userRepository,
                              SongRepository songRepository) {
        this.userInteractionRepository = userInteractionRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
    }

    @Transactional
    public void recordListenInteraction(String songId) {
        User user = getCurrentUser();
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        userInteractionRepository.findByUserAndSongAndInteractionType(user, song, "LISTEN")
                .ifPresentOrElse(
                        interaction -> {
                            interaction.setUpdatedAt(LocalDateTime.now());
                            userInteractionRepository.save(interaction);
                        },
                        () -> {
                            UserInteraction interaction = new UserInteraction();
                            interaction.setUser(user);
                            interaction.setSong(song);
                            interaction.setInteractionType("LISTEN");
                            interaction.setUpdatedAt(LocalDateTime.now());
                            userInteractionRepository.save(interaction);
                        }
                );
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
}
