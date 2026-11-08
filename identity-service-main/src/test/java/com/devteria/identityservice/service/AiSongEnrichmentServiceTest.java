package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.SongAiEnrichmentDto;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.repository.GenreRepository;
import com.devteria.identityservice.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSongEnrichmentServiceTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private GenreRepository genreRepository;

    @Mock
    private GeminiClientService geminiClientService;

    @InjectMocks
    private AiSongEnrichmentService aiSongEnrichmentService;

    private Song sampleSong;
    private Genre popGenre;
    private Genre balladGenre;

    @BeforeEach
    void setUp() {
        sampleSong = new Song();
        sampleSong.setId("song-123");
        sampleSong.setName("Nơi Này Có Anh");

        Artist artist = new Artist();
        artist.setName("Sơn Tùng M-TP");
        artist.setDescription("Ca sĩ kiêm nhạc sĩ hàng đầu V-Pop");
        sampleSong.setArtists(Set.of(artist));

        popGenre = new Genre();
        popGenre.setName("Pop");

        balladGenre = new Genre();
        balladGenre.setName("Ballad");
    }

    @Test
    @DisplayName("Làm giàu AI thành công: Cập nhật mô tả và gán thể loại khớp với DB")
    void enrichSongMetadata_whenMissingDescription_enrichesSuccessfully() {
        when(songRepository.findWithDetailsById("song-123")).thenReturn(Optional.of(sampleSong));
        when(genreRepository.findAll()).thenReturn(List.of(popGenre, balladGenre));

        SongAiEnrichmentDto aiDto = new SongAiEnrichmentDto(
                "Một bản tình ca ngọt ngào, ấm áp đầy cảm xúc.",
                List.of("Pop", "Ballad"),
                List.of("Lãng mạn", "Ấm áp")
        );

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(SongAiEnrichmentDto.class)))
                .thenReturn(Optional.of(aiDto));
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SongResponse response = aiSongEnrichmentService.enrichSongMetadata("song-123", false);

        assertThat(response).isNotNull();
        assertThat(sampleSong.getDescription()).isEqualTo("Một bản tình ca ngọt ngào, ấm áp đầy cảm xúc.");
        assertThat(sampleSong.getGenres()).contains(popGenre, balladGenre);
        verify(songRepository, times(1)).save(sampleSong);
    }

    @Test
    @DisplayName("Gemini API lỗi hoặc hết quota -> Cơ chế Fail-Open hoạt động, giữ nguyên bài hát")
    void enrichSongMetadata_whenGeminiFails_failOpenGracefully() {
        when(songRepository.findWithDetailsById("song-123")).thenReturn(Optional.of(sampleSong));
        when(genreRepository.findAll()).thenReturn(List.of(popGenre, balladGenre));

        when(geminiClientService.generateStructuredOutput(anyString(), anyString(), any(), eq(SongAiEnrichmentDto.class)))
                .thenReturn(Optional.empty());

        SongResponse response = aiSongEnrichmentService.enrichSongMetadata("song-123", false);

        assertThat(response).isNotNull();
        assertThat(sampleSong.getDescription()).isNull();
        verify(songRepository, never()).save(any());
    }
}