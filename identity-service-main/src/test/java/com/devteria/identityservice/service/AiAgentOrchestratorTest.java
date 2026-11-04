package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.PlayerActionDto;
import com.devteria.identityservice.dto.response.PlaylistProposalDto;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.util.UserIntentClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AiAgentOrchestratorTest {

    private GeminiToolDeclarationFactory toolDeclarationFactory;
    private ObjectMapper objectMapper;
    private MusicToolExecutor musicToolExecutor;
    private AiAgentOrchestrator orchestrator;

    // Subclass kiểm thử cho MusicRetrievalService không cần Mockito runtime proxy
    private static class StubMusicRetrievalService extends MusicRetrievalService {
        public StubMusicRetrievalService() {
            super(null, null, null, null);
        }

        @Override
        public List<SongCardDto> searchSongs(String query, String genre, String artist, int limit) {
            return List.of(
                    SongCardDto.builder().id("s-1").name("Nơi Này Có Anh").artistNames("Sơn Tùng M-TP").genreNames("Pop").duration(220L).build(),
                    SongCardDto.builder().id("s-2").name("Trốn Tìm").artistNames("Đen Vâu").genreNames("Rap").duration(240L).build()
            );
        }

        @Override
        public List<SongCardDto> getTopTrendingSongs(int limit) {
            return List.of(
                    SongCardDto.builder().id("trend-1").name("Trending 1").artistNames("Artist 1").duration(200L).build()
            );
        }

        @Override
        public String formatSongsAsGroundingXml(List<SongCardDto> songs) {
            return String.format("<retrieved_music_library count=\"%d\"></retrieved_music_library>", songs != null ? songs.size() : 0);
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        toolDeclarationFactory = new GeminiToolDeclarationFactory();
        musicToolExecutor = new MusicToolExecutor(new StubMusicRetrievalService());

        ExecutorService directExecutor = Executors.newSingleThreadExecutor();
        Semaphore semaphore = new Semaphore(10);

        orchestrator = new AiAgentOrchestrator(
                null,
                null,
                objectMapper,
                toolDeclarationFactory,
                musicToolExecutor,
                directExecutor,
                semaphore
        );

        ReflectionTestUtils.setField(orchestrator, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(orchestrator, "model", "gemini-3.5-flash-lite");
    }

    @Test
    @DisplayName("Tool Declaration Factory defines exactly 5 unified tools with correct parameters")
    void testToolDeclarationFactory() {
        List<Map<String, Object>> tools = toolDeclarationFactory.buildToolDeclarations();
        assertNotNull(tools);
        assertFalse(tools.isEmpty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> declarations = (List<Map<String, Object>>) tools.get(0).get("function_declarations");
        assertEquals(5, declarations.size(), "Phải có đúng 5 công cụ chuẩn hóa");

        Set<String> toolNames = new HashSet<>();
        for (Map<String, Object> dec : declarations) {
            toolNames.add((String) dec.get("name"));
        }

        assertTrue(toolNames.contains("search_music_library"));
        assertTrue(toolNames.contains("get_top_trending_songs"));
        assertTrue(toolNames.contains("get_user_recommendations"));
        assertTrue(toolNames.contains("trigger_player_action"));
        assertTrue(toolNames.contains("propose_ai_playlist"));
    }

    @Test
    @DisplayName("MusicToolExecutor executes search_music_library and registers allowed IDs")
    void testMusicToolExecutor_SearchAndRegisterAllowedIds() {
        Set<String> allowedSongIds = new HashSet<>();
        Map<String, SongCardDto> songMap = new HashMap<>();

        Map<String, Object> args = Map.of(
                "query", "nhạc Sơn Tùng",
                "limit", 5
        );

        MusicToolExecutor.ToolExecutionResult result = musicToolExecutor.executeTool(
                GeminiToolDeclarationFactory.TOOL_SEARCH_MUSIC_LIBRARY,
                args,
                "testuser",
                "Tìm nhạc Sơn Tùng",
                allowedSongIds,
                songMap
        );

        assertEquals(GeminiToolDeclarationFactory.TOOL_SEARCH_MUSIC_LIBRARY, result.toolName());
        assertNotNull(result.responsePayload().get("result"));
        assertEquals(2, allowedSongIds.size(), "2 bài hát tìm được phải được tự động đăng ký vào allowlist");
        assertTrue(allowedSongIds.contains("s-1"));
        assertTrue(allowedSongIds.contains("s-2"));
        assertTrue(songMap.containsKey("s-1"));
    }

    @Test
    @DisplayName("MusicToolExecutor strictly rejects fake song_id (Anti-Hallucination Allowlist)")
    void testMusicToolExecutor_RejectsHallucinatedSongId() {
        Set<String> allowedSongIds = new HashSet<>(List.of("valid-id-1", "valid-id-2"));
        Map<String, SongCardDto> songMap = new HashMap<>();
        songMap.put("valid-id-1", SongCardDto.builder().id("valid-id-1").name("Bài Hát Thật").build());

        // 1. LLM tự bịa ID "fake-id-999" không có trong kết quả tìm kiếm
        Map<String, Object> fakeArgs = Map.of(
                "action", "PLAY",
                "song_id", "fake-id-999"
        );

        MusicToolExecutor.ToolExecutionResult result = musicToolExecutor.executeTool(
                GeminiToolDeclarationFactory.TOOL_TRIGGER_PLAYER_ACTION,
                fakeArgs,
                "testuser",
                "Phát bài này đi",
                allowedSongIds,
                songMap
        );

        assertEquals("REJECTED", result.responsePayload().get("status"), "Phải từ chối songId giả mạo");
        assertNull(result.playerAction(), "Không được phép tạo PlayerActionDto cho bài hát giả mạo");

        // 2. LLM truyền ID hợp lệ "valid-id-1"
        Map<String, Object> validArgs = Map.of(
                "action", "PLAY",
                "song_id", "valid-id-1"
        );

        MusicToolExecutor.ToolExecutionResult validResult = musicToolExecutor.executeTool(
                GeminiToolDeclarationFactory.TOOL_TRIGGER_PLAYER_ACTION,
                validArgs,
                "testuser",
                "Phát bài này đi",
                allowedSongIds,
                songMap
        );

        assertEquals("ACCEPTED", validResult.responsePayload().get("status"));
        assertNotNull(validResult.playerAction());
        assertEquals("valid-id-1", validResult.playerAction().getSong().getId());
        assertTrue(validResult.playerAction().isAutoExecute(), "Câu lệnh 'Phát bài này đi' phải kích hoạt autoExecute = true");
    }

    @Test
    @DisplayName("MusicToolExecutor filters fake song IDs in propose_ai_playlist")
    void testMusicToolExecutor_FiltersFakeIdsInPlaylistProposal() {
        Set<String> allowedSongIds = new HashSet<>(List.of("song-1", "song-2"));
        Map<String, SongCardDto> songMap = new HashMap<>();
        songMap.put("song-1", SongCardDto.builder().id("song-1").name("Bài 1").build());
        songMap.put("song-2", SongCardDto.builder().id("song-2").name("Bài 2").build());

        // Mảng song_ids chứa cả ID hợp lệ lẫn ID bịa đặt
        Map<String, Object> args = Map.of(
                "playlist_title", "Giai Điệu Mưa",
                "song_ids", List.of("song-1", "hallucinated-id-3", "song-2")
        );

        MusicToolExecutor.ToolExecutionResult result = musicToolExecutor.executeTool(
                GeminiToolDeclarationFactory.TOOL_PROPOSE_AI_PLAYLIST,
                args,
                "testuser",
                "Tạo playlist nhé",
                allowedSongIds,
                songMap
        );

        assertEquals("ACCEPTED", result.responsePayload().get("status"));
        assertNotNull(result.playlistProposal());
        assertEquals(2, result.playlistProposal().getSongs().size(), "Phải tự động loại bỏ hallucinated-id-3 và chỉ giữ 2 bài hợp lệ");
        assertEquals("Giai Điệu Mưa", result.playlistProposal().getSuggestedTitle());
    }

    @Test
    @DisplayName("UserIntentClassifier accurately identifies direct play commands")
    void testUserIntentClassifier() {
        assertTrue(UserIntentClassifier.hasDirectPlaybackIntent("Bật bài này cho tui"));
        assertTrue(UserIntentClassifier.hasDirectPlaybackIntent("Phát bài Có Đôi Điều ngay"));
        assertTrue(UserIntentClassifier.hasDirectPlaybackIntent("mở bài trốn tìm của Đen"));
        assertTrue(UserIntentClassifier.hasDirectPlaybackIntent("play track này đi"));

        assertFalse(UserIntentClassifier.hasDirectPlaybackIntent("Bài này sáng tác năm nào vậy?"));
        assertFalse(UserIntentClassifier.hasDirectPlaybackIntent("Ý nghĩa bài hát là gì"));
        assertFalse(UserIntentClassifier.hasDirectPlaybackIntent(""));
        assertFalse(UserIntentClassifier.hasDirectPlaybackIntent(null));
    }
}
