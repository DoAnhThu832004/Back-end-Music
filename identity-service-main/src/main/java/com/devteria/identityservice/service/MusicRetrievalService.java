package com.devteria.identityservice.service;

import com.devteria.identityservice.configuration.CacheConfig;
import com.devteria.identityservice.dto.response.RecommendationResponse;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.util.VietnameseTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service trung tâm truy xuất dữ liệu kho nhạc phục vụ RAG (Retrieval-Augmented Generation) và AI Tools.
 * - Tích hợp Hybrid Search (Cosine Similarity + Từ khóa tiếng Việt không dấu).
 * - Sử dụng Caffeine Cache tối ưu hóa truy vấn bài hát thịnh hành (Top Trending).
 * - Kết nối an toàn với RecommendationEngineService cho các gợi ý cá nhân hóa theo người dùng.
 * - Triệt tiêu lỗi N+1 Query bằng @EntityGraph(attributePaths = {"artists", "genres"}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MusicRetrievalService {

    private final SongRepository songRepository;
    private final SongEmbeddingService songEmbeddingService;
    private final RecommendationEngineService recommendationEngineService;
    private final UserRepository userRepository;

    /**
     * Tìm kiếm bài hát đa tiêu chí (Hybrid Semantic Search + SQL Filter).
     *
     * @param query Từ khóa hoặc câu hỏi của người dùng (tâm trạng, chủ đề, tên bài, lời bài hát)
     * @param genre Lọc theo thể loại (tùy chọn)
     * @param artist Lọc theo nghệ sĩ (tùy chọn)
     * @param limit Số lượng kết quả tối đa
     * @return Danh sách SongCardDto đã được xếp hạng khớp nhất
     */
    @Transactional(readOnly = true)
    public List<SongCardDto> searchSongs(String query, String genre, String artist, int limit) {
        int targetLimit = Math.min(Math.max(limit, 1), 10);
        log.info("[Music Retrieval] Tìm kiếm kho nhạc: query='{}', genre='{}', artist='{}', limit={}",
                query, genre, artist, targetLimit);

        List<SongCardDto> results = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            // 1. Thực hiện Hybrid Search lấy danh sách ID bài hát có điểm tương đồng cao nhất
            List<SongEmbeddingService.ScoredSong> scoredSongs =
                    songEmbeddingService.searchSimilarSongs(query, targetLimit * 2);

            if (!scoredSongs.isEmpty()) {
                List<String> songIds = scoredSongs.stream()
                        .map(SongEmbeddingService.ScoredSong::songId)
                        .toList();

                // 2. Hydrate đầy đủ thực thể Song kèm artists & genres trong 1 query duy nhất
                List<Song> songs = songRepository.findWithDetailsByIdIn(songIds);
                Map<String, Song> songMap = songs.stream()
                        .collect(Collectors.toMap(Song::getId, s -> s, (s1, s2) -> s1));

                // Giữ nguyên thứ tự xếp hạng của thuật toán Hybrid Search
                for (SongEmbeddingService.ScoredSong scored : scoredSongs) {
                    Song s = songMap.get(scored.songId());
                    if (s != null) {
                        results.add(toSongCardDto(s));
                    }
                }
            }
        }

        // Nếu Hybrid Search không trả về kết quả hoặc query rỗng, lấy từ kho bài hát phổ biến
        if (results.isEmpty()) {
            results = getTopTrendingSongs(targetLimit * 2);
        }

        // 3. Áp dụng bộ lọc tùy chọn (Genre & Artist) nếu người dùng chỉ định
        if (genre != null && !genre.isBlank()) {
            results = results.stream()
                    .filter(s -> s.getGenreNames() != null &&
                            VietnameseTextNormalizer.containsIgnoreCaseAndAccent(s.getGenreNames(), genre))
                    .collect(Collectors.toList());
        }

        if (artist != null && !artist.isBlank()) {
            results = results.stream()
                    .filter(s -> s.getArtistNames() != null &&
                            VietnameseTextNormalizer.containsIgnoreCaseAndAccent(s.getArtistNames(), artist))
                    .collect(Collectors.toList());
        }

        return results.stream().limit(targetLimit).toList();
    }

    /**
     * Lấy danh sách bài hát thịnh hành nhất hệ thống (Top Trending theo lượt nghe).
     * Kết quả được lưu vào Caffeine Cache ("topTrendingSongsCache") với TTL = 5 phút.
     *
     * @param limit Số lượng bài hát cần lấy
     * @return Danh sách SongCardDto
     */
    @Cacheable(value = CacheConfig.TOP_TRENDING_SONGS_CACHE, key = "#limit")
    @Transactional(readOnly = true)
    public List<SongCardDto> getTopTrendingSongs(int limit) {
        int targetLimit = Math.min(Math.max(limit, 1), 20);
        log.info("[Music Retrieval] Truy vấn Top Trending từ DB (Cache Miss): limit={}", targetLimit);

        Page<Song> songPage = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, targetLimit));
        return songPage.getContent().stream()
                .map(this::toSongCardDto)
                .toList();
    }

    /**
     * Lấy danh sách bài hát gợi ý cá nhân hóa cho người dùng qua RecommendationEngineService.
     * Hỗ trợ lọc theo tâm trạng (mood_filter).
     *
     * @param currentUsername Tên đăng nhập người dùng từ SecurityContext
     * @param moodFilter Bộ lọc tâm trạng tùy chọn (ví dụ: "buồn", "vui", "chill", "sôi động")
     * @param limit Số lượng bài hát đề xuất
     * @return Danh sách SongCardDto
     */
    @Transactional(readOnly = true)
    public List<SongCardDto> getUserRecommendations(String currentUsername, String moodFilter, int limit) {
        int targetLimit = Math.min(Math.max(limit, 1), 10);
        if (currentUsername == null || currentUsername.isBlank()) {
            return getTopTrendingSongs(targetLimit);
        }

        try {
            Optional<User> userOpt = userRepository.findByUsername(currentUsername);
            if (userOpt.isEmpty()) {
                log.warn("[Music Retrieval] Không tìm thấy user {}, fallback về Top Trending.", currentUsername);
                return getTopTrendingSongs(targetLimit);
            }

            String userId = userOpt.get().getId();
            RecommendationResponse recResponse =
                    recommendationEngineService.getRecommendations(userId, targetLimit * 2);

            if (recResponse == null || recResponse.getSongs() == null || recResponse.getSongs().isEmpty()) {
                return getTopTrendingSongs(targetLimit);
            }

            List<SongResponse> recommendedSongResponses = recResponse.getSongs();
            List<String> songIds = recommendedSongResponses.stream()
                    .map(SongResponse::getId)
                    .toList();

            List<Song> songs = songRepository.findWithDetailsByIdIn(songIds);
            Map<String, Song> songMap = songs.stream()
                    .collect(Collectors.toMap(Song::getId, s -> s, (s1, s2) -> s1));

            List<SongCardDto> dtoList = new ArrayList<>();
            for (SongResponse sr : recommendedSongResponses) {
                Song s = songMap.get(sr.getId());
                if (s != null) {
                    dtoList.add(toSongCardDto(s));
                }
            }

            // Lọc theo tâm trạng nếu có chỉ định moodFilter
            if (moodFilter != null && !moodFilter.isBlank()) {
                List<SongCardDto> filtered = dtoList.stream()
                        .filter(dto -> {
                            boolean inDesc = dto.getDescription() != null &&
                                    VietnameseTextNormalizer.containsIgnoreCaseAndAccent(dto.getDescription(), moodFilter);
                            boolean inGenre = dto.getGenreNames() != null &&
                                    VietnameseTextNormalizer.containsIgnoreCaseAndAccent(dto.getGenreNames(), moodFilter);
                            boolean inTitle = dto.getName() != null &&
                                    VietnameseTextNormalizer.containsIgnoreCaseAndAccent(dto.getName(), moodFilter);
                            return inDesc || inGenre || inTitle;
                        })
                        .toList();

                if (!filtered.isEmpty()) {
                    return filtered.stream().limit(targetLimit).toList();
                }
            }

            return dtoList.stream().limit(targetLimit).toList();

        } catch (Exception e) {
            log.warn("[Music Retrieval] Lỗi khi lấy gợi ý cá nhân hóa cho {} (Fail-Safe fallback): {}",
                    currentUsername, e.getMessage());
            return getTopTrendingSongs(targetLimit);
        }
    }

    /**
     * Đóng gói danh sách bài hát thành chuỗi XML Grounding an toàn gửi cho Gemini LLM trong functionResponse.
     * Tự động escape các ký tự đặc biệt chống Prompt Injection từ metadata bài hát.
     *
     * @param songs Danh sách SongCardDto
     * @return Chuỗi XML an toàn <retrieved_music_library count="...">...</retrieved_music_library>
     */
    public String formatSongsAsGroundingXml(List<SongCardDto> songs) {
        if (songs == null || songs.isEmpty()) {
            return "<retrieved_music_library count=\"0\">\n</retrieved_music_library>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<retrieved_music_library count=\"%d\">\n", songs.size()));

        for (SongCardDto song : songs) {
            sb.append(String.format(
                    "  <song id=\"%s\" title=\"%s\" artists=\"%s\" genres=\"%s\" duration=\"%ds\" />\n",
                    escapeXml(song.getId()),
                    escapeXml(song.getName()),
                    escapeXml(song.getArtistNames()),
                    escapeXml(song.getGenreNames()),
                    song.getDuration() != null ? song.getDuration() : 0
            ));
        }

        sb.append("</retrieved_music_library>");
        return sb.toString();
    }

    /**
     * Chuyển đổi thực thể Song sang SongCardDto kèm thông tin nghệ sĩ và thể loại đã được format.
     */
    public SongCardDto toSongCardDto(Song song) {
        if (song == null) {
            return null;
        }

        String artistNames = (song.getArtists() != null && !song.getArtists().isEmpty())
                ? song.getArtists().stream().map(Artist::getName).collect(Collectors.joining(", "))
                : "Chưa rõ";

        String genreNames = (song.getGenres() != null && !song.getGenres().isEmpty())
                ? song.getGenres().stream().map(Genre::getName).collect(Collectors.joining(", "))
                : "Chưa rõ";

        return SongCardDto.builder()
                .id(song.getId())
                .name(song.getName())
                .artistNames(artistNames)
                .genreNames(genreNames)
                .description(song.getDescription())
                .imageUrl(song.getImageUrl())
                .audioUrl(song.getAudioUrl())
                .duration(song.getDuration())
                .playCount(song.getPlayCount() != null ? song.getPlayCount() : 0L)
                .build();
    }

    /**
     * Escape ký tự XML để ngăn ngừa XML Injection và Prompt Injection trong phản hồi Tool.
     */
    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
