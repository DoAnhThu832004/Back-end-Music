package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.RecommendationResponse;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MusicRetrievalServiceTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private UserRepository userRepository;

    private TestableSongEmbeddingService songEmbeddingService;
    private TestableRecommendationEngineService recommendationEngineService;
    private MusicRetrievalService musicRetrievalService;

    // Subclass kiểm thử tất định cho SongEmbeddingService
    private static class TestableSongEmbeddingService extends SongEmbeddingService {
        private List<ScoredSong> mockedResults = Collections.emptyList();

        public TestableSongEmbeddingService() {
            super(null, new ObjectMapper(), null, null);
        }

        public void setMockedResults(List<ScoredSong> results) {
            this.mockedResults = results;
        }

        @Override
        public List<ScoredSong> searchSimilarSongs(String query, int limit) {
            return mockedResults;
        }
    }

    // Subclass kiểm thử tất định cho RecommendationEngineService (tương thích 100% JDK 23)
    private static class TestableRecommendationEngineService extends RecommendationEngineService {
        private RecommendationResponse mockedResponse;

        public TestableRecommendationEngineService() {
            super(null, null, null, null, null, null, null, null);
        }

        public void setMockedResponse(RecommendationResponse response) {
            this.mockedResponse = response;
        }

        @Override
        public RecommendationResponse getRecommendations(String userId, int limit) {
            return mockedResponse;
        }
    }

    @BeforeEach
    void setUp() {
        songEmbeddingService = new TestableSongEmbeddingService();
        recommendationEngineService = new TestableRecommendationEngineService();
        musicRetrievalService = new MusicRetrievalService(
                songRepository,
                songEmbeddingService,
                recommendationEngineService,
                userRepository
        );
    }

    private Song createSampleSong(String id, String name, String artistName, String genreName, Long playCount) {
        Artist artist = new Artist();
        artist.setName(artistName);

        Genre genre = new Genre();
        genre.setName(genreName);

        Song song = new Song();
        song.setId(id);
        song.setName(name);
        song.setDescription("Mô tả bài hát " + name);
        song.setDuration(240L);
        song.setPlayCount(playCount);
        song.setImageUrl("https://example.com/" + id + ".jpg");
        song.setAudioUrl("https://example.com/" + id + ".mp3");
        song.setArtists(Set.of(artist));
        song.setGenres(Set.of(genre));
        return song;
    }

    @Test
    @DisplayName("searchSongs uses Hybrid Search and preserves score ranking")
    void testSearchSongs_PreservesRanking() {
        Song s1 = createSampleSong("s-1", "Nơi Này Có Anh", "Sơn Tùng M-TP", "V-Pop", 5000L);
        Song s2 = createSampleSong("s-2", "Trốn Tìm", "Đen Vâu", "Rap", 3000L);

        songEmbeddingService.setMockedResults(List.of(
                new SongEmbeddingService.ScoredSong("s-1", 0.95, 0.8, 0.39),
                new SongEmbeddingService.ScoredSong("s-2", 0.40, 0.3, 0.19)
        ));

        when(songRepository.findWithDetailsByIdIn(List.of("s-1", "s-2")))
                .thenReturn(List.of(s2, s1)); // Trả về thứ tự ngẫu nhiên từ DB

        List<SongCardDto> results = musicRetrievalService.searchSongs("son tung", null, null, 5);

        assertEquals(2, results.size());
        assertEquals("s-1", results.get(0).getId(), "Bài 1 phải đứng đầu theo thứ tự điểm số của Hybrid Search");
        assertEquals("s-2", results.get(1).getId());
        assertEquals("Sơn Tùng M-TP", results.get(0).getArtistNames());
    }

    @Test
    @DisplayName("searchSongs filters by genre and artist if provided")
    void testSearchSongs_WithFilters() {
        Song s1 = createSampleSong("s-1", "Nơi Này Có Anh", "Sơn Tùng M-TP", "Pop", 5000L);
        Song s2 = createSampleSong("s-2", "Trốn Tìm", "Đen Vâu", "Rap", 3000L);

        songEmbeddingService.setMockedResults(List.of(
                new SongEmbeddingService.ScoredSong("s-1", 0.8, 0.7, 0.2),
                new SongEmbeddingService.ScoredSong("s-2", 0.85, 0.7, 0.3)
        ));

        when(songRepository.findWithDetailsByIdIn(anyList()))
                .thenReturn(List.of(s1, s2));

        // Lọc chỉ lấy bài có thể loại Rap
        List<SongCardDto> rapOnly = musicRetrievalService.searchSongs("nhạc chill", "rap", null, 5);
        assertEquals(1, rapOnly.size());
        assertEquals("s-2", rapOnly.get(0).getId());

        // Lọc theo nghệ sĩ Sơn Tùng không dấu
        List<SongCardDto> sonTungOnly = musicRetrievalService.searchSongs("nhạc chill", null, "son tung", 5);
        assertEquals(1, sonTungOnly.size());
        assertEquals("s-1", sonTungOnly.get(0).getId());
    }

    @Test
    @DisplayName("getTopTrendingSongs queries repository by playCount with dynamic limit")
    void testGetTopTrendingSongs() {
        Song s1 = createSampleSong("s-1", "Bài Hot 1", "Ca sĩ 1", "Pop", 10000L);
        Song s2 = createSampleSong("s-2", "Bài Hot 2", "Ca sĩ 2", "Ballad", 8000L);

        when(songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, 2)))
                .thenReturn(new PageImpl<>(List.of(s1, s2)));

        List<SongCardDto> trending = musicRetrievalService.getTopTrendingSongs(2);

        assertEquals(2, trending.size());
        assertEquals(10000L, trending.get(0).getPlayCount());
        assertEquals("Bài Hot 1", trending.get(0).getName());
    }

    @Test
    @DisplayName("getUserRecommendations retrieves personalized songs and handles fallback safely")
    void testGetUserRecommendations_SafeFallback() {
        // Trường hợp username null -> fallback Top Trending
        Song trendingSong = createSampleSong("t-1", "Trending Song", "Artist", "Pop", 9999L);
        when(songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(trendingSong)));

        List<SongCardDto> nullUserRes = musicRetrievalService.getUserRecommendations(null, null, 5);
        assertEquals(1, nullUserRes.size());
        assertEquals("t-1", nullUserRes.get(0).getId());

        // Trường hợp User hợp lệ
        User user = new User();
        user.setId("u-123");
        user.setUsername("music_lover");

        when(userRepository.findByUsername("music_lover")).thenReturn(Optional.of(user));

        SongResponse sr = new SongResponse();
        sr.setId("rec-1");
        RecommendationResponse recResponse = new RecommendationResponse();
        recResponse.setSongs(List.of(sr));

        recommendationEngineService.setMockedResponse(recResponse);

        Song recSong = createSampleSong("rec-1", "Nhạc Đề Xuất", "Artist Rec", "Indie", 1200L);
        when(songRepository.findWithDetailsByIdIn(List.of("rec-1"))).thenReturn(List.of(recSong));

        List<SongCardDto> recResults = musicRetrievalService.getUserRecommendations("music_lover", null, 5);
        assertEquals(1, recResults.size());
        assertEquals("rec-1", recResults.get(0).getId());
    }

    @Test
    @DisplayName("formatSongsAsGroundingXml generates valid and XML-escaped XML string")
    void testFormatSongsAsGroundingXml() {
        SongCardDto song1 = SongCardDto.builder()
                .id("song-1")
                .name("Rock & Roll <Mùa Hè>")
                .artistNames("Đen & Bạn 'Thân'")
                .genreNames("Rap/Hip-hop")
                .duration(215L)
                .build();

        String xml = musicRetrievalService.formatSongsAsGroundingXml(List.of(song1));

        assertTrue(xml.contains("<retrieved_music_library count=\"1\">"));
        assertTrue(xml.contains("Rock &amp; Roll &lt;Mùa Hè&gt;"), "Tên bài hát phải được escape ký tự & và < >");
        assertTrue(xml.contains("Đen &amp; Bạn &apos;Thân&apos;"), "Tên nghệ sĩ phải được escape & và dấu nháy");
        assertTrue(xml.contains("duration=\"215s\""));
        assertTrue(xml.endsWith("</retrieved_music_library>"));

        // Kiểm tra danh sách rỗng
        String emptyXml = musicRetrievalService.formatSongsAsGroundingXml(Collections.emptyList());
        assertEquals("<retrieved_music_library count=\"0\">\n</retrieved_music_library>", emptyXml);
    }
}
