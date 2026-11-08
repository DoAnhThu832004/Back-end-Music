package com.devteria.identityservice.service;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Factory định nghĩa JSON Schema cho 5 công cụ chuẩn hóa (Unified Gemini Tools) theo chuẩn OpenAPI/Gemini API v1beta.
 */
@Component
public class GeminiToolDeclarationFactory {

    public static final String TOOL_SEARCH_MUSIC_LIBRARY = "search_music_library";
    public static final String TOOL_GET_TOP_TRENDING = "get_top_trending_songs";
    public static final String TOOL_GET_USER_RECOMMENDATIONS = "get_user_recommendations";
    public static final String TOOL_TRIGGER_PLAYER_ACTION = "trigger_player_action";
    public static final String TOOL_PROPOSE_AI_PLAYLIST = "propose_ai_playlist";

    /**
     * Tạo danh sách Tool Declarations chuẩn gửi trong request payload của Gemini API.
     *
     * @return Danh sách cấu trúc tools theo định dạng Gemini Function Calling
     */
    public List<Map<String, Object>> buildToolDeclarations() {
        List<Map<String, Object>> declarations = new ArrayList<>();

        declarations.add(createSearchMusicLibraryDeclaration());
        declarations.add(createGetTopTrendingSongsDeclaration());
        declarations.add(createGetUserRecommendationsDeclaration());
        declarations.add(createTriggerPlayerActionDeclaration());
        declarations.add(createProposeAiPlaylistDeclaration());

        return List.of(Map.of("function_declarations", declarations));
    }

    private Map<String, Object> createSearchMusicLibraryDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "STRING",
                "description", "Từ khóa tìm kiếm hoặc câu miêu tả tâm trạng, bối cảnh, thể loại âm nhạc cần tìm."
        ));
        properties.put("genre", Map.of(
                "type", "STRING",
                "description", "Lọc theo thể loại âm nhạc cụ thể (ví dụ: Pop, Ballad, Rap, Indie, EDM, R&B)."
        ));
        properties.put("artist", Map.of(
                "type", "STRING",
                "description", "Lọc theo tên nghệ sĩ hoặc ca sĩ thể hiện."
        ));
        properties.put("limit", Map.of(
                "type", "INTEGER",
                "description", "Số lượng bài hát tối đa cần lấy (mặc định 5, tối đa 10)."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        parameters.put("required", List.of("query"));

        return Map.of(
                "name", TOOL_SEARCH_MUSIC_LIBRARY,
                "description", "Tìm kiếm bài hát trong kho nhạc của ứng dụng theo tên bài, nghệ sĩ, thể loại, hoặc theo tâm trạng/chủ đề (RAG Hybrid Semantic Search).",
                "parameters", parameters
        );
    }

    private Map<String, Object> createGetTopTrendingSongsDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("limit", Map.of(
                "type", "INTEGER",
                "description", "Số lượng bài hát tối đa cần lấy (mặc định 5, tối đa 10)."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);

        return Map.of(
                "name", TOOL_GET_TOP_TRENDING,
                "description", "Lấy danh sách các bài hát đang thịnh hành (Trending) có lượt nghe nhiều nhất trên hệ thống ứng dụng.",
                "parameters", parameters
        );
    }

    private Map<String, Object> createGetUserRecommendationsDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("mood_filter", Map.of(
                "type", "STRING",
                "description", "Lọc theo tâm trạng tùy chọn (ví dụ: vui, buồn, thư giãn, chill, tập trung, sôi động)."
        ));
        properties.put("limit", Map.of(
                "type", "INTEGER",
                "description", "Số lượng bài hát đề xuất tối đa (mặc định 5, tối đa 10)."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);

        return Map.of(
                "name", TOOL_GET_USER_RECOMMENDATIONS,
                "description", "Lấy danh sách bài hát gợi ý cá nhân hóa dựa trên gu âm nhạc và lịch sử nghe của người dùng hiện tại.",
                "parameters", parameters
        );
    }

    private Map<String, Object> createTriggerPlayerActionDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "STRING",
                "enum", List.of("PLAY", "QUEUE"),
                "description", "Hành động điều khiển trình phát: 'PLAY' để phát ngay lập tức, 'QUEUE' để thêm bài hát vào hàng đợi phát."
        ));
        properties.put("song_id", Map.of(
                "type", "STRING",
                "description", "Mã định danh duy nhất (song_id) của bài hát lấy từ kết quả truy xuất kho nhạc vừa nhận được."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        parameters.put("required", List.of("action", "song_id"));

        return Map.of(
                "name", TOOL_TRIGGER_PLAYER_ACTION,
                "description", "Đề xuất hoặc kích hoạt hành động phát ngay (PLAY) hoặc thêm vào hàng đợi (QUEUE) một bài hát cụ thể từ kết quả đã tìm thấy.",
                "parameters", parameters
        );
    }

    private Map<String, Object> createProposeAiPlaylistDeclaration() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("playlist_title", Map.of(
                "type", "STRING",
                "description", "Tiêu đề hấp dẫn, phù hợp với chủ đề hoặc tâm trạng cho danh sách phát được đề xuất (ví dụ: 'Vibe Đêm Khuya Cùng DJ GenZ')."
        ));
        properties.put("song_ids", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING"),
                "description", "Mảng các mã bài hát (song_ids) được tuyển chọn từ kết quả tìm kiếm để đưa vào danh sách phát."
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "OBJECT");
        parameters.put("properties", properties);
        parameters.put("required", List.of("playlist_title", "song_ids"));

        return Map.of(
                "name", TOOL_PROPOSE_AI_PLAYLIST,
                "description", "Đề xuất một danh sách phát (Playlist) mới gồm các bài hát chọn lọc để người dùng xem trước và bấm xác nhận lưu vào thư viện cá nhân.",
                "parameters", parameters
        );
    }
}
