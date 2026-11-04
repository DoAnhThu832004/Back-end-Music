package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.PlayerActionDto;
import com.devteria.identityservice.dto.response.PlaylistProposalDto;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.util.UserIntentClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.devteria.identityservice.service.GeminiToolDeclarationFactory.*;

/**
 * Thực thi các Tool âm nhạc được gọi bởi Gemini LLM trong vòng lặp Multi-Step Agent.
 * Chịu trách nhiệm hậu kiểm chống ảo giác (Anti-Hallucination Allowlist Validation).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MusicToolExecutor {

    private final MusicRetrievalService musicRetrievalService;

    public record ToolExecutionResult(
            String toolName,
            Map<String, Object> responsePayload,
            PlayerActionDto playerAction,
            PlaylistProposalDto playlistProposal,
            List<SongCardDto> newSuggestedSongs
    ) {}

    /**
     * Thực thi một Function Call nhận được từ phản hồi của Gemini API.
     *
     * @param toolName Tên công cụ (search_music_library, get_top_trending_songs, ...)
     * @param args Tham số đầu vào do Gemini trích xuất
     * @param currentUsername Tên người dùng đang đăng nhập
     * @param userRawPrompt Câu lệnh ban đầu của người dùng (phục vụ intent classification)
     * @param turnAllowedSongIds Danh sách ID bài hát được phép trong lượt chat này
     * @param retrievedSongsMap Bản đồ cache các bài hát đã tìm được trong lượt chat
     * @return Kết quả thực thi công cụ
     */
    public ToolExecutionResult executeTool(
            String toolName,
            Map<String, Object> args,
            String currentUsername,
            String userRawPrompt,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        log.info("[Music Tool Executor] Thực thi tool: '{}' với args: {}", toolName, args);

        Map<String, Object> safeArgs = (args != null) ? args : Collections.emptyMap();

        try {
            return switch (toolName) {
                case TOOL_SEARCH_MUSIC_LIBRARY -> executeSearchMusicLibrary(safeArgs, turnAllowedSongIds, retrievedSongsMap);
                case TOOL_GET_TOP_TRENDING -> executeGetTopTrending(safeArgs, turnAllowedSongIds, retrievedSongsMap);
                case TOOL_GET_USER_RECOMMENDATIONS -> executeGetUserRecommendations(safeArgs, currentUsername, turnAllowedSongIds, retrievedSongsMap);
                case TOOL_TRIGGER_PLAYER_ACTION -> executeTriggerPlayerAction(safeArgs, userRawPrompt, turnAllowedSongIds, retrievedSongsMap);
                case TOOL_PROPOSE_AI_PLAYLIST -> executeProposeAiPlaylist(safeArgs, turnAllowedSongIds, retrievedSongsMap);
                default -> {
                    log.warn("[Music Tool Executor] Tool không được hỗ trợ: {}", toolName);
                    yield new ToolExecutionResult(
                            toolName,
                            Map.of("error", "Tool không được hỗ trợ: " + toolName),
                            null, null, Collections.emptyList()
                    );
                }
            };
        } catch (Exception e) {
            log.error("[Music Tool Executor] Lỗi khi thực thi tool '{}': {}", toolName, e.getMessage(), e);
            return new ToolExecutionResult(
                    toolName,
                    Map.of("error", "Lỗi nội bộ khi thực thi công cụ: " + e.getMessage()),
                    null, null, Collections.emptyList()
            );
        }
    }

    private ToolExecutionResult executeSearchMusicLibrary(
            Map<String, Object> args,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        String query = (String) args.get("query");
        String genre = (String) args.get("genre");
        String artist = (String) args.get("artist");
        int limit = parseLimit(args.get("limit"), 5);

        List<SongCardDto> songs = musicRetrievalService.searchSongs(query, genre, artist, limit);
        registerRetrievedSongs(songs, turnAllowedSongIds, retrievedSongsMap);

        String xmlGrounding = musicRetrievalService.formatSongsAsGroundingXml(songs);
        return new ToolExecutionResult(
                TOOL_SEARCH_MUSIC_LIBRARY,
                Map.of("result", xmlGrounding),
                null, null, songs
        );
    }

    private ToolExecutionResult executeGetTopTrending(
            Map<String, Object> args,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        int limit = parseLimit(args.get("limit"), 5);
        List<SongCardDto> songs = musicRetrievalService.getTopTrendingSongs(limit);
        registerRetrievedSongs(songs, turnAllowedSongIds, retrievedSongsMap);

        String xmlGrounding = musicRetrievalService.formatSongsAsGroundingXml(songs);
        return new ToolExecutionResult(
                TOOL_GET_TOP_TRENDING,
                Map.of("result", xmlGrounding),
                null, null, songs
        );
    }

    private ToolExecutionResult executeGetUserRecommendations(
            Map<String, Object> args,
            String currentUsername,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        String moodFilter = (String) args.get("mood_filter");
        int limit = parseLimit(args.get("limit"), 5);

        List<SongCardDto> songs = musicRetrievalService.getUserRecommendations(currentUsername, moodFilter, limit);
        registerRetrievedSongs(songs, turnAllowedSongIds, retrievedSongsMap);

        String xmlGrounding = musicRetrievalService.formatSongsAsGroundingXml(songs);
        return new ToolExecutionResult(
                TOOL_GET_USER_RECOMMENDATIONS,
                Map.of("result", xmlGrounding),
                null, null, songs
        );
    }

    private ToolExecutionResult executeTriggerPlayerAction(
            Map<String, Object> args,
            String userRawPrompt,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        String action = (String) args.get("action");
        String songId = (String) args.get("song_id");

        if (action == null || (!"PLAY".equalsIgnoreCase(action) && !"QUEUE".equalsIgnoreCase(action))) {
            return new ToolExecutionResult(
                    TOOL_TRIGGER_PLAYER_ACTION,
                    Map.of("status", "REJECTED", "reason", "Action không hợp lệ. Chỉ chấp nhận 'PLAY' hoặc 'QUEUE'."),
                    null, null, Collections.emptyList()
            );
        }

        // HẬU KIỂM BẮT BUỘC (AC-RAG-01): Chống ảo giác ID
        if (songId == null || !turnAllowedSongIds.contains(songId)) {
            log.warn("[Anti-Hallucination] LLM đã gọi trigger_player_action với songId '{}' không nằm trong allowlist {}!",
                    songId, turnAllowedSongIds);
            return new ToolExecutionResult(
                    TOOL_TRIGGER_PLAYER_ACTION,
                    Map.of("status", "REJECTED", "reason", "Mã bài hát không tồn tại trong kết quả tìm kiếm vừa nhận."),
                    null, null, Collections.emptyList()
            );
        }

        SongCardDto targetSong = retrievedSongsMap.get(songId);
        boolean autoExecute = UserIntentClassifier.hasDirectPlaybackIntent(userRawPrompt);

        PlayerActionDto playerAction = PlayerActionDto.builder()
                .action(action.toUpperCase())
                .song(targetSong)
                .autoExecute(autoExecute)
                .build();

        log.info("[Music Tool Executor] Đã chấp thuận PlayerAction: action={}, song='{}', autoExecute={}",
                action, targetSong != null ? targetSong.getName() : songId, autoExecute);

        return new ToolExecutionResult(
                TOOL_TRIGGER_PLAYER_ACTION,
                Map.of("status", "ACCEPTED", "action", action, "song_name", targetSong != null ? targetSong.getName() : songId),
                playerAction, null, Collections.emptyList()
        );
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionResult executeProposeAiPlaylist(
            Map<String, Object> args,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {

        String playlistTitle = (String) args.get("playlist_title");
        Object songIdsObj = args.get("song_ids");

        List<String> rawSongIds = new ArrayList<>();
        if (songIdsObj instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    rawSongIds.add(s);
                }
            }
        }

        // Lọc chống ảo giác ID: chỉ giữ lại các ID thực sự nằm trong allowlist
        List<SongCardDto> validPlaylistSongs = new ArrayList<>();
        for (String id : rawSongIds) {
            if (turnAllowedSongIds.contains(id) && retrievedSongsMap.containsKey(id)) {
                validPlaylistSongs.add(retrievedSongsMap.get(id));
            } else {
                log.warn("[Anti-Hallucination] Loại bỏ songId '{}' không nằm trong allowlist khỏi playlist proposal", id);
            }
        }

        if (validPlaylistSongs.isEmpty()) {
            return new ToolExecutionResult(
                    TOOL_PROPOSE_AI_PLAYLIST,
                    Map.of("status", "REJECTED", "reason", "Không có bài hát hợp lệ nào để tạo playlist đề xuất."),
                    null, null, Collections.emptyList()
            );
        }

        String finalTitle = (playlistTitle != null && !playlistTitle.isBlank())
                ? playlistTitle.trim()
                : "Playlist AI Đề Xuất";

        PlaylistProposalDto proposal = PlaylistProposalDto.builder()
                .suggestedTitle(finalTitle)
                .songs(validPlaylistSongs)
                .build();

        log.info("[Music Tool Executor] Đã tạo PlaylistProposal: title='{}', song_count={}",
                finalTitle, validPlaylistSongs.size());

        return new ToolExecutionResult(
                TOOL_PROPOSE_AI_PLAYLIST,
                Map.of("status", "ACCEPTED", "title", finalTitle, "valid_song_count", validPlaylistSongs.size()),
                null, proposal, Collections.emptyList()
        );
    }

    private void registerRetrievedSongs(
            List<SongCardDto> songs,
            Set<String> turnAllowedSongIds,
            Map<String, SongCardDto> retrievedSongsMap) {
        if (songs != null) {
            for (SongCardDto song : songs) {
                if (song.getId() != null) {
                    turnAllowedSongIds.add(song.getId());
                    retrievedSongsMap.put(song.getId(), song);
                }
            }
        }
    }

    private int parseLimit(Object limitObj, int defaultVal) {
        if (limitObj instanceof Number num) {
            return Math.min(Math.max(num.intValue(), 1), 10);
        }
        return defaultVal;
    }
}
