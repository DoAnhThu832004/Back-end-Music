package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.SongEmbedding;
import com.devteria.identityservice.repository.SongEmbeddingRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SongEmbeddingServiceTest {

    @Mock
    private RestClient geminiRestClient;

    @Mock
    private SongEmbeddingRepository songEmbeddingRepository;

    @Mock
    private SongRepository songRepository;

    private ObjectMapper objectMapper;
    private SongEmbeddingService songEmbeddingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        songEmbeddingService = new SongEmbeddingService(
                geminiRestClient,
                objectMapper,
                songEmbeddingRepository,
                songRepository
        );
        ReflectionTestUtils.setField(songEmbeddingService, "apiKey", "test-key");
        ReflectionTestUtils.setField(songEmbeddingService, "embeddingModel", "gemini-embedding-001");
    }

    @Test
    @DisplayName("Cosine similarity returns 1.0 for identical vectors")
    void testCosineSimilarity_IdenticalVectors() {
        float[] v1 = new float[]{1.0f, 2.0f, 3.0f};
        float[] v2 = new float[]{1.0f, 2.0f, 3.0f};

        double similarity = songEmbeddingService.cosineSimilarity(v1, v2);
        assertEquals(1.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("Cosine similarity returns 0.0 for orthogonal vectors")
    void testCosineSimilarity_OrthogonalVectors() {
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{0.0f, 1.0f, 0.0f};

        double similarity = songEmbeddingService.cosineSimilarity(v1, v2);
        assertEquals(0.0, similarity, 0.0001);
    }

    @Test
    @DisplayName("Cosine similarity handles null, empty or mismatched lengths safely")
    void testCosineSimilarity_EdgeCases() {
        assertEquals(0.0, songEmbeddingService.cosineSimilarity(null, new float[]{1.0f}));
        assertEquals(0.0, songEmbeddingService.cosineSimilarity(new float[]{1.0f}, null));
        assertEquals(0.0, songEmbeddingService.cosineSimilarity(new float[0], new float[0]));
        assertEquals(0.0, songEmbeddingService.cosineSimilarity(new float[]{1.0f}, new float[]{1.0f, 2.0f}));
        assertEquals(0.0, songEmbeddingService.cosineSimilarity(new float[]{0.0f, 0.0f}, new float[]{0.0f, 0.0f}));
    }

    @Test
    @DisplayName("Hybrid search prioritizes exact and non-accented Vietnamese keyword match")
    void testHybridSearch_KeywordBonusMatching() {
        // Mock 3 bài hát trong bộ đệm In-Memory
        songEmbeddingService.putCacheForTesting(
                "song-1",
                "noi nay co anh",
                "Tên bài hát: Nơi Này Có Anh | Nghệ sĩ: Sơn Tùng M-TP | Thể loại: Pop, V-Pop | Mô tả: lãng mạn tình yêu",
                new float[]{0.1f, 0.2f, 0.3f}
        );

        songEmbeddingService.putCacheForTesting(
                "song-2",
                "tron tim",
                "Tên bài hát: Trốn Tìm | Nghệ sĩ: Đen Vâu | Thể loại: Rap, Hip-hop | Mô tả: tuổi thơ hoài niệm",
                new float[]{0.4f, 0.5f, 0.6f}
        );

        songEmbeddingService.putCacheForTesting(
                "song-3",
                "buoc qua nhau",
                "Tên bài hát: Bước Qua Nhau | Nghệ sĩ: Vũ | Thể loại: Indie, Ballad | Mô tả: nhạc buồn đêm mưa chia tay",
                new float[]{0.7f, 0.8f, 0.9f}
        );

        // 1. Tìm kiếm không dấu "son tung" -> Phải tìm ra bài 1 đầu tiên với điểm keyword cao
        List<SongEmbeddingService.ScoredSong> sonTungResults = songEmbeddingService.searchSimilarSongs("son tung", 5);
        assertFalse(sonTungResults.isEmpty());
        assertEquals("song-1", sonTungResults.get(0).songId());
        assertTrue(sonTungResults.get(0).keywordBonus() > 0.0);

        // 2. Tìm kiếm không dấu "den vau" -> Phải tìm ra bài 2 đầu tiên
        List<SongEmbeddingService.ScoredSong> denVauResults = songEmbeddingService.searchSimilarSongs("den vau", 5);
        assertFalse(denVauResults.isEmpty());
        assertEquals("song-2", denVauResults.get(0).songId());

        // 3. Tìm kiếm theo chủ đề/tâm trạng "nhạc buồn đêm mưa" -> Khớp bài 3
        List<SongEmbeddingService.ScoredSong> sadResults = songEmbeddingService.searchSimilarSongs("nhạc buồn đêm mưa", 5);
        assertFalse(sadResults.isEmpty());
        assertEquals("song-3", sadResults.get(0).songId());
    }

    @Test
    @DisplayName("Hybrid search returns empty list when query is blank or cache is empty")
    void testHybridSearch_BlankQueryAndEmptyCache() {
        assertTrue(songEmbeddingService.searchSimilarSongs("", 5).isEmpty());
        assertTrue(songEmbeddingService.searchSimilarSongs("   ", 5).isEmpty());
        assertTrue(songEmbeddingService.searchSimilarSongs(null, 5).isEmpty());

        songEmbeddingService.clearCacheForTesting();
        assertTrue(songEmbeddingService.searchSimilarSongs("Sơn Tùng", 5).isEmpty());
    }

    @Test
    @DisplayName("Sync song embedding persists entity and updates cache")
    void testSyncSongEmbedding() {
        Artist artist = new Artist();
        artist.setName("Đen Vâu");

        Genre genre = new Genre();
        genre.setName("Rap");

        Song song = new Song();
        song.setId("test-song-id");
        song.setName("Đi Về Nhà");
        song.setDescription("Bài hát ấm áp về gia đình Tết");
        song.setArtists(Set.of(artist));
        song.setGenres(Set.of(genre));

        when(songEmbeddingRepository.findBySongId("test-song-id")).thenReturn(Optional.empty());
        when(songEmbeddingRepository.save(any(SongEmbedding.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SongEmbedding result = songEmbeddingService.syncSongEmbedding(song);

        assertNotNull(result);
        assertEquals("test-song-id", result.getSongId());
        assertEquals("di ve nha", result.getNormalizedTitle());
        assertTrue(result.getSearchText().contains("Đi Về Nhà"));
        assertTrue(result.getSearchText().contains("Đen Vâu"));

        verify(songEmbeddingRepository, times(1)).save(any(SongEmbedding.class));
        assertTrue(songEmbeddingService.containsInCache("test-song-id"));
    }
}
