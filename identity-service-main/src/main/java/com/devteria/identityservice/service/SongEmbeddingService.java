package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.SongEmbedding;
import com.devteria.identityservice.repository.SongEmbeddingRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.util.VietnameseTextNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Service quản lý Vector Embedding cho bài hát phục vụ Hybrid Search (In-Memory Cosine Similarity + Keyword Match).
 * Tích hợp Gemini Embedding API (gemini-embedding-001 hoặc text-embedding-004) với chuẩn 768 chiều.
 */
@Service
@Slf4j
public class SongEmbeddingService {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;
    private final SongEmbeddingRepository songEmbeddingRepository;
    private final SongRepository songRepository;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    // Bộ đệm In-Memory cực nhanh (ConcurrentHashMap) lưu trữ vector và metadata chuẩn hóa
    private final ConcurrentMap<String, CachedSongMetadata> cache = new ConcurrentHashMap<>();

    public record CachedSongMetadata(
            String songId,
            String normalizedTitle,
            String normalizedSearchText,
            float[] embedding
    ) {}

    public record ScoredSong(
            String songId,
            double score,
            double cosineScore,
            double keywordBonus
    ) {}

    @Autowired
    public SongEmbeddingService(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            ObjectMapper objectMapper,
            SongEmbeddingRepository songEmbeddingRepository,
            SongRepository songRepository) {
        this.geminiRestClient = geminiRestClient;
        this.objectMapper = objectMapper;
        this.songEmbeddingRepository = songEmbeddingRepository;
        this.songRepository = songRepository;
    }

    /**
     * Tải toàn bộ dữ liệu vector nhúng từ cơ sở dữ liệu lên bộ nhớ đệm In-Memory khi ứng dụng khởi động.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeEmbeddingsIfEmpty() {
        log.info("[Hybrid Search] Khởi động nạp bộ nhớ đệm SongEmbedding...");
        loadCacheFromDatabase();

        try {
            List<Song> allSongs = songRepository.findAll();
            int newlySeeded = 0;

            for (Song song : allSongs) {
                if (!cache.containsKey(song.getId())) {
                    try {
                        syncSongEmbedding(song);
                        newlySeeded++;
                        // Giữ nhịp gọi API để tránh vượt hạn mức rate limit
                        Thread.sleep(150);
                    } catch (Exception e) {
                        log.warn("[Hybrid Search] Không thể tạo embedding cho bài hát {} ({}): {}",
                                song.getName(), song.getId(), e.getMessage());
                    }
                }
            }

            if (newlySeeded > 0) {
                log.info("[Hybrid Search] Đã hoàn thành batch seeding cho {} bài hát mới.", newlySeeded);
            }
        } catch (Exception e) {
            log.warn("[Hybrid Search] Quá trình batch seeding gặp ngoại lệ (Fail-Safe): {}", e.getMessage());
        }
    }

    /**
     * Nạp dữ liệu từ DB vào RAM cache.
     */
    public synchronized void loadCacheFromDatabase() {
        try {
            List<SongEmbedding> allEmbeddings = songEmbeddingRepository.findAll();
            for (SongEmbedding se : allEmbeddings) {
                try {
                    float[] vector = objectMapper.readValue(se.getEmbeddingJson(), float[].class);
                    cache.put(se.getSongId(), new CachedSongMetadata(
                            se.getSongId(),
                            se.getNormalizedTitle(),
                            VietnameseTextNormalizer.normalize(se.getSearchText()),
                            vector
                    ));
                } catch (Exception e) {
                    log.warn("[Hybrid Search] Lỗi giải mã vector cho songId {}: {}", se.getSongId(), e.getMessage());
                }
            }
            log.info("[Hybrid Search] Đã nạp {} vector bài hát vào bộ đệm RAM.", cache.size());
        } catch (Exception e) {
            log.warn("[Hybrid Search] Lỗi nạp cache từ database: {}", e.getMessage());
        }
    }

    /**
     * Gọi Gemini API sinh vector nhúng 768 chiều cho văn bản đầu vào.
     *
     * @param text Văn bản cần nhúng ngữ nghĩa
     * @return Mảng float 768 chiều, hoặc mảng rỗng nếu có lỗi (Fail-Open)
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank() || apiKey == null || apiKey.isBlank()) {
            return new float[0];
        }

        try {
            Map<String, Object> requestPayload = Map.of(
                    "model", "models/" + embeddingModel,
                    "content", Map.of("parts", List.of(Map.of("text", text.trim()))),
                    "outputDimensionality", 768
            );

            String requestJson = objectMapper.writeValueAsString(requestPayload);
            String endpoint = "/models/" + embeddingModel + ":embedContent";

            String responseJson = geminiRestClient.post()
                    .uri(endpoint)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                return new float[0];
            }

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode valuesNode = root.path("embedding").path("values");
            if (valuesNode.isArray() && !valuesNode.isEmpty()) {
                float[] vector = new float[valuesNode.size()];
                for (int i = 0; i < valuesNode.size(); i++) {
                    vector[i] = (float) valuesNode.get(i).asDouble();
                }
                return vector;
            }

        } catch (Exception e) {
            log.warn("[Hybrid Search] Lỗi khi gọi Gemini embedContent cho văn bản [{}]: {}",
                    text.substring(0, Math.min(30, text.length())), e.getMessage());
        }

        return new float[0];
    }

    /**
     * Tính toán khoảng cách ngữ nghĩa Cosine Similarity giữa 2 vector float.
     *
     * @param a Vector A
     * @param b Vector B
     * @return Điểm số tương đồng trong khoảng [-1.0, 1.0]
     */
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Tìm kiếm bài hát kết hợp (Hybrid Search):
     * Điểm số = (Cosine Similarity * 0.7) + Điểm thưởng khớp từ khóa tiếng Việt không dấu.
     *
     * @param query Câu hỏi hoặc từ khóa tìm kiếm của người dùng
     * @param limit Số lượng kết quả tối đa cần lấy
     * @return Danh sách ScoredSong sắp xếp theo điểm giảm dần
     */
    public List<ScoredSong> searchSimilarSongs(String query, int limit) {
        if (query == null || query.isBlank() || cache.isEmpty()) {
            return Collections.emptyList();
        }

        String normQuery = VietnameseTextNormalizer.normalize(query);
        List<String> queryTokens = VietnameseTextNormalizer.tokenize(query);
        float[] queryVector = generateEmbedding(query);
        boolean hasQueryVector = (queryVector != null && queryVector.length > 0);

        List<ScoredSong> results = new ArrayList<>();

        for (CachedSongMetadata item : cache.values()) {
            double cosine = 0.0;
            if (hasQueryVector && item.embedding() != null && item.embedding().length == queryVector.length) {
                cosine = Math.max(0.0, cosineSimilarity(queryVector, item.embedding()));
            }

            // Tính điểm thưởng từ khóa không dấu (Keyword Bonus)
            double bonus = 0.0;
            if (!normQuery.isEmpty()) {
                if (item.normalizedTitle().equals(normQuery)) {
                    bonus += 0.45; // Khớp chính xác hoàn toàn tên bài
                } else if (item.normalizedTitle().contains(normQuery)) {
                    bonus += 0.30; // Chứa chuỗi tên bài
                } else if (item.normalizedSearchText().contains(normQuery)) {
                    bonus += 0.20; // Chứa trong tên nghệ sĩ/thể loại/mô tả
                }

                // Điểm cộng dồn cho từng từ đơn lẻ khớp
                int matchedTokens = 0;
                for (String token : queryTokens) {
                    if (item.normalizedTitle().contains(token)) {
                        matchedTokens++;
                    }
                }
                if (!queryTokens.isEmpty()) {
                    bonus += ((double) matchedTokens / queryTokens.size()) * 0.15;
                }
            }

            // Điểm kết hợp Hybrid (Semantic 70% + Keyword 30% + Bonus)
            double finalScore = (cosine * 0.7) + bonus;

            // Ngưỡng tối thiểu để giữ lại bài hát có liên quan
            if (finalScore >= 0.15 || bonus >= 0.20) {
                results.add(new ScoredSong(item.songId(), finalScore, cosine, bonus));
            }
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));

        int targetLimit = Math.max(1, limit);
        return results.stream().limit(targetLimit).toList();
    }

    /**
     * Đồng bộ hóa và cập nhật vector embedding cho một bài hát vào DB và In-Memory cache.
     *
     * @param song Thực thể bài hát
     * @return Thực thể SongEmbedding đã lưu
     */
    @Transactional
    public SongEmbedding syncSongEmbedding(Song song) {
        if (song == null || song.getId() == null) {
            return null;
        }

        String artistNames = (song.getArtists() != null && !song.getArtists().isEmpty())
                ? song.getArtists().stream().map(Artist::getName).collect(Collectors.joining(", "))
                : "Chưa rõ";

        String genreNames = (song.getGenres() != null && !song.getGenres().isEmpty())
                ? song.getGenres().stream().map(Genre::getName).collect(Collectors.joining(", "))
                : "Chưa rõ";

        String description = (song.getDescription() != null && !song.getDescription().isBlank())
                ? song.getDescription()
                : "";

        String searchText = String.format("Tên bài hát: %s | Nghệ sĩ: %s | Thể loại: %s | Mô tả: %s",
                song.getName(), artistNames, genreNames, description).trim();

        String normalizedTitle = VietnameseTextNormalizer.normalize(song.getName());
        float[] vector = generateEmbedding(searchText);

        String vectorJson = "[]";
        try {
            vectorJson = objectMapper.writeValueAsString(vector);
        } catch (Exception e) {
            log.error("[Hybrid Search] Lỗi chuyển đổi vector sang JSON: {}", e.getMessage());
        }

        SongEmbedding embedding = songEmbeddingRepository.findBySongId(song.getId())
                .orElse(SongEmbedding.builder()
                        .songId(song.getId())
                        .song(song)
                        .build());

        embedding.setSearchText(searchText);
        embedding.setNormalizedTitle(normalizedTitle);
        embedding.setEmbeddingJson(vectorJson);

        SongEmbedding saved = songEmbeddingRepository.save(embedding);

        // Cập nhật bộ nhớ đệm
        cache.put(song.getId(), new CachedSongMetadata(
                song.getId(),
                normalizedTitle,
                VietnameseTextNormalizer.normalize(searchText),
                vector
        ));

        log.info("[Hybrid Search] Đã đồng bộ embedding cho bài hát: {} (ID: {}, Vector size: {})",
                song.getName(), song.getId(), vector.length);

        return saved;
    }

    /**
     * Lấy kích thước bộ nhớ đệm hiện tại.
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Kiểm tra bài hát đã có trong bộ nhớ đệm chưa.
     */
    public boolean containsInCache(String songId) {
        return cache.containsKey(songId);
    }

    /**
     * Trực tiếp cập nhật cache (dùng cho Unit Test hoặc Mocking).
     */
    public void putCacheForTesting(String songId, String normalizedTitle, String searchText, float[] embedding) {
        cache.put(songId, new CachedSongMetadata(
                songId,
                normalizedTitle,
                VietnameseTextNormalizer.normalize(searchText),
                embedding
        ));
    }

    /**
     * Xóa cache (dùng cho Unit Test).
     */
    public void clearCacheForTesting() {
        cache.clear();
    }
}
