package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.AlbumResponse;
import com.devteria.identityservice.dto.response.ArtistResponse;
import com.devteria.identityservice.dto.response.GenreResponse;
import com.devteria.identityservice.dto.response.HomeRecommendationResponse;
import com.devteria.identityservice.dto.response.PlaylistResponse;
import com.devteria.identityservice.dto.response.RecommendationResponse;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Album;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Playlist;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.SongSimilarity;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.entity.UserInteraction;
import com.devteria.identityservice.repository.AlbumRepository;
import com.devteria.identityservice.repository.ArtistRepository;
import com.devteria.identityservice.repository.FollowerRepository;
import com.devteria.identityservice.repository.PlaylistRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.SongSimilarityRepository;
import com.devteria.identityservice.repository.UserInteractionRepository;
import com.devteria.identityservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RecommendationEngineService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEngineService.class);

    /**
     * λ trong thuật toán MMR (Maximal Marginal Relevance).
     * λ = 0.7: ưu tiên 70% relevance, 30% diversity.
     * Tăng λ để ưu tiên điểm cao hơn, giảm λ để danh sách đa dạng hơn.
     */
    private static final double MMR_LAMBDA = 0.7;

    /**
     * Số lượng candidate tối đa lấy cho mỗi bài hát đã nghe.
     */
    private static final int CANDIDATES_PER_SONG = 20;

    /** Số bài hát rộng để làm đầu vào cho Aggregation. */
    private static final int HOME_SONG_POOL = 50;

    /** Số Artist/Album/Playlist tối đa trả về cho trang chủ. */
    private static final int HOME_TOP_K = 5;

    private static final double BASE_FOLLOW_BOOST = 3.0;

    private static final double RECENCY_DECAY_LAMBDA = 0.05;

    private final UserRepository userRepository;
    private final UserInteractionRepository userInteractionRepository;
    private final SongSimilarityRepository songSimilarityRepository;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final PlaylistRepository playlistRepository;
    private final FollowerRepository followerRepository;

    public RecommendationEngineService(
            UserRepository userRepository,
            UserInteractionRepository userInteractionRepository,
            SongSimilarityRepository songSimilarityRepository,
            SongRepository songRepository,
            ArtistRepository artistRepository,
            AlbumRepository albumRepository,
            PlaylistRepository playlistRepository,
            FollowerRepository followerRepository) {
        this.userRepository = userRepository;
        this.userInteractionRepository = userInteractionRepository;
        this.songSimilarityRepository = songSimilarityRepository;
        this.songRepository = songRepository;
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.playlistRepository = playlistRepository;
        this.followerRepository = followerRepository;
    }

    // =========================================================
    // ENTRY POINT: Bước 2.1 — API Handler
    // =========================================================

    public RecommendationResponse getRecommendations(String userId, int limit) {
        log.info("=== [ONLINE] Bắt đầu lấy gợi ý cho user: {} ===", userId);

        // Bước 2.2: Cold Start Check
        boolean hasHistory = userInteractionRepository.existsByUserId(userId);
        if (!hasHistory) {
            log.info("  [COLD START] User {} chưa có lịch sử nghe nhạc.", userId);
            return handleColdStart(userId, limit);
        }

        return getPersonalizedRecommendations(userId, limit);
    }

    // =========================================================
    // ENTRY POINT HOME: API Trang Chủ — Songs + Artists + Albums + Playlists
    // =========================================================

    public HomeRecommendationResponse getHomeRecommendations(String userId) {
        log.info("=== [HOME] Bắt đầu lấy gợi ý trang chủ cho user: {} ===", userId);

        boolean hasHistory = userInteractionRepository.existsByUserId(userId);

        if (!hasHistory) {
            log.info("  [HOME][COLD START] User {} chưa có lịch sử.", userId);
            return buildColdStartHome(userId);
        }

        return buildPersonalizedHome(userId);
    }

    private HomeRecommendationResponse buildPersonalizedHome(String userId) {
        // ---- H.1: Lấy pool 50 bài hát (tái dụng pipeline có sẵn) ----
        // Không dùng getPersonalizedRecommendations() vì cần Song entity để aggregate Playlist
        List<UserInteraction> interactions = userInteractionRepository.findAllByUserId(userId);
        Map<String, Double> userProfile = buildUserProfile(interactions);
        Set<String> listenedSongs = userProfile.keySet();

        Map<String, Double> candidateScores = generateCandidates(userProfile, listenedSongs);

        if (candidateScores.isEmpty()) {
            log.warn("  [HOME][FALLBACK] Không có candidate. Fallback về Cold Start Home.");
            return buildColdStartHome(userId);
        }

        List<Song> songPool;
        String source;
        List<String> topCandidateIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit((long) HOME_SONG_POOL * 3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<Song> candidateSongs = songRepository.findByIdIn(topCandidateIds);
        Map<String, Song> songById = candidateSongs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        // Follow Artist + New Release Boost (cũng áp dụng cho trang chủ)
        Set<String> followedArtistIds = getFollowedArtistIds(userId);
        if (!followedArtistIds.isEmpty()) {
            log.info("  [HOME][FOLLOW BOOST] User follow {} nghệ sĩ. Áp dụng New Release Boost.",
                    followedArtistIds.size());
            applyFollowArtistBoost(candidateScores, songById, followedArtistIds);
        }

        songPool = mmrRerank(candidateScores, songById, HOME_SONG_POOL);
        source = "PERSONALIZED";
        log.info("  [HOME] Pool: {} bài hát (source: {})", songPool.size(), source);

        // ---- H.2: Aggregate in-memory ----
        // Tính điểm cho mỗi Artist/Album/Playlist từ pool bài hát
        Map<String, Double> artistScores = new HashMap<>();
        Map<String, Double> albumScores  = new HashMap<>();
        Map<String, Double> playlistScores = new HashMap<>();

        for (int i = 0; i < songPool.size(); i++) {
            Song song = songPool.get(i);
            // Dùng vị trí đảo ngược làm điểm: bài đầu tiên có điểm cao nhất
            double score = candidateScores.getOrDefault(
                    song.getId(),
                    (double) (songPool.size() - i)  // fallback nếu từ trending
            );

            // Cộng điểm cho từng Artist của bài
            if (song.getArtists() != null) {
                for (Artist artist : song.getArtists()) {
                    artistScores.merge(artist.getId(), score, Double::sum);
                }
            }

            // Cộng điểm cho Album (nếu có)
            if (song.getAlbum() != null) {
                albumScores.merge(song.getAlbum().getId(), score, Double::sum);
            }

            // Cộng điểm cho từng Playlist chứa bài này
            if (song.getPlaylists() != null) {
                for (Playlist playlist : song.getPlaylists()) {
                    playlistScores.merge(playlist.getId(), score, Double::sum);
                }
            }
        }
        log.info("  [HOME][AGG] {} artists, {} albums, {} playlists được tính điểm",
                artistScores.size(), albumScores.size(), playlistScores.size());

        // ---- H.3: Sort & Truncate ----
        List<String> topArtistIds  = topKIds(artistScores,  HOME_TOP_K);
        List<String> topAlbumIds   = topKIds(albumScores,   HOME_TOP_K);
        List<String> topPlaylistIds = topKIds(playlistScores, HOME_TOP_K);

        // ---- H.4: Hydration ----
        List<Artist>   artists   = artistRepository.findAllByIdIn(topArtistIds);
        List<Album>    albums    = albumRepository.findAllByIdIn(topAlbumIds);
        List<Playlist> playlists;
        if (!topPlaylistIds.isEmpty()) {
            playlists = playlistRepository.findAllById(topPlaylistIds);
        } else {
            // Nếu không suy ra được playlist nào, fallback lấy playlist admin
            playlists = playlistRepository.findAllAdminPlaylists().stream()
                    .limit(HOME_TOP_K)
                    .collect(Collectors.toList());
        }

        // Sắp xếp lại theo thứ tự điểm số (hydration không giữ thứ tự)
        Map<String, Double> artistScoresFinal  = artistScores;
        Map<String, Double> albumScoresFinal   = albumScores;
        artists.sort(Comparator.comparingDouble(
                a -> -artistScoresFinal.getOrDefault(a.getId(), 0.0)));
        albums.sort(Comparator.comparingDouble(
                a -> -albumScoresFinal.getOrDefault(a.getId(), 0.0)));

        // ---- H.5: Build Response ----
        List<SongResponse> songResponses = toSongResponseList(
                songPool.size() > 10 ? songPool.subList(0, 10) : songPool
        );
        List<ArtistResponse>   artistResponses   = artists.stream().map(this::toArtistResponse).collect(Collectors.toList());
        List<AlbumResponse>    albumResponses    = albums.stream().map(this::toAlbumResponse).collect(Collectors.toList());
        List<PlaylistResponse> playlistResponses = playlists.stream().map(this::toPlaylistResponse).collect(Collectors.toList());

        log.info("  [HOME] Trả về: {} songs, {} artists, {} albums, {} playlists",
                songResponses.size(), artistResponses.size(), albumResponses.size(), playlistResponses.size());

        return new HomeRecommendationResponse(source, songResponses, artistResponses, albumResponses, playlistResponses);
    }

    /**
     * Trang chủ Cold Start: user chưa có lịch sử tương tác.
     * Dùng preferredGenres để query Trending Songs/Artists/Albums.
     * Fallback về Global Trending nếu user chưa chọn thể loại.
     */
    private HomeRecommendationResponse buildColdStartHome(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        boolean hasGenres = userOpt.isPresent()
                && userOpt.get().getPreferredGenres() != null
                && !userOpt.get().getPreferredGenres().isEmpty();

        String source;
        List<Song>    songs    = new ArrayList<>();
        List<Artist>  artists  = new ArrayList<>();
        List<Album>   albums   = new ArrayList<>();
        List<Playlist> playlists;

        if (hasGenres) {
            source = "COLD_START_GENRE";
            User user = userOpt.get();
            log.info("  [HOME][COLD START] Trending theo {} genres yêu thích",
                    user.getPreferredGenres().size());

            // Songs: gộp trending của tất cả preferredGenres, deduplicate
            Map<String, Song> songMap = new LinkedHashMap<>();
            for (var genre : user.getPreferredGenres()) {
                songRepository.findTop10ByGenres_IdOrderByPlayCountDesc(genre.getId())
                        .forEach(s -> songMap.put(s.getId(), s));
            }
            songs = new ArrayList<>(songMap.values());
            songs.sort(Comparator.comparingLong(s -> -(s.getPlayCount() != null ? s.getPlayCount() : 0L)));
            if (songs.size() > 10) songs = songs.subList(0, 10);

            // Artists: lấy top artist theo genre (query DB, 1 lần mỗi genre)
            Map<String, Artist> artistMap = new LinkedHashMap<>();
            for (var genre : user.getPreferredGenres()) {
                artistRepository.findTopArtistsByGenreId(
                        genre.getId(), PageRequest.of(0, HOME_TOP_K)
                ).forEach(a -> artistMap.put(a.getId(), a));
            }
            artists = new ArrayList<>(artistMap.values());
            if (artists.size() > HOME_TOP_K) artists = artists.subList(0, HOME_TOP_K);

            // Albums: lấy top album theo genre
            Map<String, Album> albumMap = new LinkedHashMap<>();
            for (var genre : user.getPreferredGenres()) {
                albumRepository.findTopAlbumsByGenreId(
                        genre.getId(), PageRequest.of(0, HOME_TOP_K)
                ).forEach(a -> albumMap.put(a.getId(), a));
            }
            albums = new ArrayList<>(albumMap.values());
            if (albums.size() > HOME_TOP_K) albums = albums.subList(0, HOME_TOP_K);

        } else {
            source = "COLD_START_GLOBAL";
            log.info("  [HOME][COLD START] User chưa chọn thể loại. Global Trending.");
            songs = songRepository.findTop10ByOrderByPlayCountDesc();
        }

        // Playlists: luôn lấy playlist Admin (System Playlists) cho Cold Start
        playlists = playlistRepository.findAllAdminPlaylists().stream()
                .limit(HOME_TOP_K)
                .collect(Collectors.toList());

        return new HomeRecommendationResponse(
                source,
                toSongResponseList(songs),
                artists.stream().map(this::toArtistResponse).collect(Collectors.toList()),
                albums.stream().map(this::toAlbumResponse).collect(Collectors.toList()),
                playlists.stream().map(this::toPlaylistResponse).collect(Collectors.toList())
        );
    }

    /**
     * Helper: Sắp xếp Map<id, score> và lấy Top K id có điểm cao nhất.
     * Chạy trên RAM, O(N log N).
     */
    private List<String> topKIds(Map<String, Double> scores, int k) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    // =========================================================
    // BƯỚC 2.2: COLD START
    // =========================================================

    /**
     * Bước 2.2 — Xử lý Cold Start:
     * User chưa có lịch sử → trả về Trending theo thể loại yêu thích,
     * hoặc Global Trending nếu chưa chọn thể loại.
     */
    private RecommendationResponse handleColdStart(String userId, int limit) {
        Optional<User> userOpt = userRepository.findById(userId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Kiểm tra xem user đã chọn preferred genres khi đăng ký chưa
            if (user.getPreferredGenres() != null && !user.getPreferredGenres().isEmpty()) {
                log.info("  [COLD START] Trả về Trending theo {} thể loại yêu thích",
                        user.getPreferredGenres().size());

                // Lấy bài trending từ các genre yêu thích (gộp và deduplicate)
                Map<String, Song> songMap = new HashMap<>();
                for (var genre : user.getPreferredGenres()) {
                    List<Song> genreTrending = songRepository
                            .findTop10ByGenres_IdOrderByPlayCountDesc(genre.getId());
                    for (Song s : genreTrending) {
                        songMap.put(s.getId(), s);
                    }
                }

                List<Song> songs = new ArrayList<>(songMap.values());
                // Sắp xếp lại theo playCount và lấy top limit
                songs.sort(Comparator.comparingLong(s -> -(s.getPlayCount() != null ? s.getPlayCount() : 0L)));
                if (songs.size() > limit) songs = songs.subList(0, limit);

                return new RecommendationResponse("COLD_START_GENRE", toSongResponseList(songs));
            }
        }

        // Fallback: Global Trending
        log.info("  [COLD START] User chưa chọn thể loại. Trả về Global Trending.");
        List<Song> globalTrending = songRepository.findTop10ByOrderByPlayCountDesc();
        if (globalTrending.size() > limit) globalTrending = globalTrending.subList(0, limit);

        return new RecommendationResponse("COLD_START_GLOBAL", toSongResponseList(globalTrending));
    }

    // =========================================================
    // BƯỚC 2.3 → 2.7: PERSONALIZED RECOMMENDATIONS
    // =========================================================

    private RecommendationResponse getPersonalizedRecommendations(String userId, int limit) {

        // ---- Bước 2.3: Lấy User Profile ----
        List<UserInteraction> interactions = userInteractionRepository.findAllByUserId(userId);
        Map<String, Double> userProfile = buildUserProfile(interactions);
        Set<String> listenedSongs = userProfile.keySet();
        log.info("  [PROFILE] User đã tương tác với {} bài hát", listenedSongs.size());

        // ---- Bước 2.4: Candidate Generation ----
        Map<String, Double> candidateScores = generateCandidates(userProfile, listenedSongs);
        log.info("  [CANDIDATES] Tìm thấy {} bài hát ứng viên", candidateScores.size());

        if (candidateScores.isEmpty()) {
            log.warn("  [FALLBACK] Không có candidate. Fallback về Cold Start logic.");
            return handleColdStart(userId, limit);
        }

        // ---- Bước 2.5: Predicted Score (đã được tính trong generateCandidates) ----
        // Lấy thông tin đầy đủ của các bài hát candidate
        List<String> topCandidateIds = candidateScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit((long) limit * 5)  // Lấy gấp 5 lần để MMR có đủ để chọn
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Song> candidateSongs = songRepository.findByIdIn(topCandidateIds);

        // Tạo map để lookup nhanh theo ID
        Map<String, Song> songById = candidateSongs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        // ---- Bước 2.5b: Follow Artist + New Release Boost ----
        // Lấy danh sách Artist mà user đang follow để cộng điểm ưu tiên
        Set<String> followedArtistIds = getFollowedArtistIds(userId);
        if (!followedArtistIds.isEmpty()) {
            log.info("  [FOLLOW BOOST] User follow {} nghệ sĩ. Áp dụng New Release Boost.",
                    followedArtistIds.size());
            applyFollowArtistBoost(candidateScores, songById, followedArtistIds);
        }

        // ---- Bước 2.6: MMR Re-ranking ----
        log.info("  [MMR] Áp dụng MMR re-ranking với λ={}", MMR_LAMBDA);
        List<Song> rerankedSongs = mmrRerank(candidateScores, songById, limit);
        log.info("  [MMR] Chọn được {} bài sau re-ranking", rerankedSongs.size());

        // ---- Bước 2.7: Trả về kết quả ----
        return new RecommendationResponse("PERSONALIZED", toSongResponseList(rerankedSongs));
    }

    // =========================================================
    // FOLLOW ARTIST + NEW RELEASE BOOST
    // =========================================================

    /**
     * Lấy tập hợp ID của tất cả Nghệ sĩ mà user đang Follow.
     * Dùng để kiểm tra bài hát ứng viên có thuộc Artist được follow không.
     *
     * @param userId ID của user
     * @return Set chứa các Artist ID
     */
    private Set<String> getFollowedArtistIds(String userId) {
        try {
            // Cần lấy User entity để query FollowerRepository
            return userRepository.findById(userId)
                    .map(user -> followerRepository.findAllByUser(user).stream()
                            .map(f -> f.getArtist().getId())
                            .collect(Collectors.toSet()))
                    .orElse(new HashSet<>());
        } catch (Exception e) {
            log.warn("  [FOLLOW BOOST] Không lấy được danh sách follow của user {}: {}", userId, e.getMessage());
            return new HashSet<>();
        }
    }

    private void applyFollowArtistBoost(
            Map<String, Double> candidateScores,
            Map<String, Song> songById,
            Set<String> followedIds) {

        int boostedCount = 0;
        for (Map.Entry<String, Song> entry : songById.entrySet()) {
            String songId = entry.getKey();
            Song song = entry.getValue();

            if (song.getArtists() == null || song.getArtists().isEmpty()) continue;

            // Kiểm tra bài hát có thuộc Artist được follow không
            boolean isFollowedArtist = song.getArtists().stream()
                    .anyMatch(artist -> followedIds.contains(artist.getId()));

            if (isFollowedArtist) {
                double recencyFactor = calculateRecencyFactor(song.getReleasedDate());
                double boost = BASE_FOLLOW_BOOST * recencyFactor;

                // Cộng boost vào điểm hiện có (hoặc tạo entry mới nếu chưa có)
                candidateScores.merge(songId, boost, Double::sum);
                boostedCount++;

                log.debug("  [FOLLOW BOOST] Bài '{}': recency={:.3f}, boost=+{:.3f}",
                        song.getName(), recencyFactor, boost);
            }
        }
        log.info("  [FOLLOW BOOST] Đã boost {} bài hát từ nghệ sĩ được follow.", boostedCount);
    }

    private double calculateRecencyFactor(LocalDateTime releasedDate) {
        if (releasedDate == null) return 0.0;
        long daysOld = ChronoUnit.DAYS.between(releasedDate, LocalDateTime.now());
        if (daysOld < 0) daysOld = 0; // Đề phòng ngày ra mắt ở tương lai gần
        return Math.exp(-RECENCY_DECAY_LAMBDA * daysOld);
    }

    // =========================================================
    // BƯỚC 2.3: XÂY DỰNG USER PROFILE
    // =========================================================

    private Map<String, Double> buildUserProfile(List<UserInteraction> interactions) {
        Map<String, Double> profile = new HashMap<>();
        for (UserInteraction ui : interactions) {
            if (ui.getSong() != null && ui.getRatingScore() != null) {
                String songId = ui.getSong().getId();
                profile.merge(songId, ui.getRatingScore().doubleValue(), Double::sum);
            }
        }
        return profile;
    }

    private Map<String, Double> generateCandidates(
            Map<String, Double> userProfile, Set<String> listenedSongs) {

        // numerator[songId] = Σ(userScore × similarity)
        Map<String, Double> numeratorMap = new HashMap<>();
        // denominator[songId] = Σ(|similarity|)
        Map<String, Double> denominatorMap = new HashMap<>();

        for (Map.Entry<String, Double> entry : userProfile.entrySet()) {
            String heardSongId = entry.getKey();
            double userScore = entry.getValue();

            // Lấy top-N bài tương tự với heardSong từ bảng song_similarity
            // (giới hạn CANDIDATES_PER_SONG để tránh load toàn bộ vào RAM)
            List<SongSimilarity> similar = songSimilarityRepository
                    .findAllRelatedToSong(heardSongId, PageRequest.of(0, CANDIDATES_PER_SONG));

            for (SongSimilarity ss : similar) {
                // Lấy ID của bài kia trong cặp (không phải bài đang xét)
                String candidateId = ss.getSongAId().equals(heardSongId)
                        ? ss.getSongBId()
                        : ss.getSongAId();

                // Loại bỏ bài user đã nghe rồi
                if (listenedSongs.contains(candidateId)) continue;

                double sim = ss.getSimilarityScore();
                numeratorMap.merge(candidateId, userScore * sim, Double::sum);
                denominatorMap.merge(candidateId, Math.abs(sim), Double::sum);
            }
        }

        // Tính predicted score cuối cùng
        Map<String, Double> predictedScores = new HashMap<>();
        for (String candidateId : numeratorMap.keySet()) {
            double denom = denominatorMap.getOrDefault(candidateId, 1.0);
            if (denom > 0) {
                predictedScores.put(candidateId, numeratorMap.get(candidateId) / denom);
            }
        }

        return predictedScores;
    }

    private List<Song> mmrRerank(
            Map<String, Double> candidateScores,
            Map<String, Song> songById,
            int limit) {

        // Chuẩn hóa predicted score về [0, 1] để so sánh với Jaccard
        double maxScore = candidateScores.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);

        List<Song> selected = new ArrayList<>();
        Set<String> selectedIds = new HashSet<>();

        // Lấy pool ứng viên còn lại
        Set<String> remainingIds = new HashSet<>(songById.keySet());

        while (selected.size() < limit && !remainingIds.isEmpty()) {
            String bestId = null;
            double bestMmrScore = Double.NEGATIVE_INFINITY;

            for (String candidateId : remainingIds) {
                Song candidate = songById.get(candidateId);
                if (candidate == null) continue;

                // Relevance: normalized predicted score
                double relevance = candidateScores.getOrDefault(candidateId, 0.0) / maxScore;

                // Diversity: max Jaccard similarity với bài đã được chọn
                double maxSimilarityToSelected = 0.0;
                if (!selected.isEmpty()) {
                    for (Song s : selected) {
                        double jaccard = computeGenreJaccard(candidate, s);
                        maxSimilarityToSelected = Math.max(maxSimilarityToSelected, jaccard);
                    }
                }

                // MMR score
                double mmrScore = MMR_LAMBDA * relevance
                        - (1 - MMR_LAMBDA) * maxSimilarityToSelected;

                if (mmrScore > bestMmrScore) {
                    bestMmrScore = mmrScore;
                    bestId = candidateId;
                }
            }

            if (bestId != null) {
                Song chosenSong = songById.get(bestId);
                selected.add(chosenSong);
                selectedIds.add(bestId);
                remainingIds.remove(bestId);
                log.debug("  [MMR] Chọn bài: {} (MMR score: {:.4f})", chosenSong.getName(), bestMmrScore);
            } else {
                break;
            }
        }

        return selected;
    }

    private double computeGenreJaccard(Song a, Song b) {
        if (a.getGenres() == null || a.getGenres().isEmpty()
                || b.getGenres() == null || b.getGenres().isEmpty()) {
            return 0.0;
        }

        Set<String> genresA = a.getGenres().stream()
                .map(g -> g.getId()).collect(Collectors.toSet());
        Set<String> genresB = b.getGenres().stream()
                .map(g -> g.getId()).collect(Collectors.toSet());

        Set<String> intersection = new HashSet<>(genresA);
        intersection.retainAll(genresB);

        Set<String> union = new HashSet<>(genresA);
        union.addAll(genresB);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    // =========================================================
    // HELPER: Entity → DTO Converters
    // =========================================================

    /**
     * Chuyển đổi danh sách Song entity sang SongResponse DTO.
     */
    private List<SongResponse> toSongResponseList(List<Song> songs) {
        List<SongResponse> result = new ArrayList<>();
        for (Song song : songs) {
            SongResponse dto = new SongResponse();
            dto.setId(song.getId());
            dto.setName(song.getName());
            dto.setDescription(song.getDescription());
            dto.setDuration(song.getDuration());
            dto.setReleasedDate(song.getReleasedDate());
            dto.setImageUrl(song.getImageUrl());
            dto.setAudioUrl(song.getAudioUrl());
            dto.setPlayCount(song.getPlayCount());
            dto.setStatus(song.getStatus());
            dto.setType(song.getType());

            // Map tên nghệ sĩ đầu tiên nếu có
            if (song.getArtists() != null && !song.getArtists().isEmpty()) {
                String artistName = song.getArtists().iterator().next().getName();
                dto.setArtistName(artistName);
            }

            // Map danh sách genre
            if (song.getGenres() != null) {
                List<GenreResponse> genres = song.getGenres().stream()
                        .map(g -> {
                            GenreResponse gr = new GenreResponse();
                            gr.setId(g.getId());
                            gr.setName(g.getName());
                            gr.setKeyG(g.getKeyG());
                            return gr;
                        })
                        .collect(Collectors.toList());
                dto.setGenres(genres);
            }

            result.add(dto);
        }
        return result;
    }

    /**
     * Chuyển Artist entity → ArtistResponse DTO (không include nested songs/albums
     * để tránh N+1 query và giữ response nhẹ cho trang chủ).
     */
    private ArtistResponse toArtistResponse(Artist artist) {
        ArtistResponse dto = new ArtistResponse();
        dto.setId(artist.getId());
        dto.setName(artist.getName());
        dto.setDescription(artist.getDescription());
        dto.setImageUrlAr(artist.getImageUrlAr());
        dto.setTotalFollowers(artist.getTotalFollowers());
        return dto;
    }

    /**
     * Chuyển Album entity → AlbumResponse DTO (không include nested songs
     * để giữ response nhẹ cho trang chủ).
     */
    private AlbumResponse toAlbumResponse(Album album) {
        AlbumResponse dto = new AlbumResponse();
        dto.setId(album.getId());
        dto.setName(album.getName());
        dto.setDescription(album.getDescription());
        dto.setStatus(album.getStatus());
        dto.setImageUrlA(album.getImageUrlA());
        return dto;
    }

    /**
     * Chuyển Playlist entity → PlaylistResponse DTO.
     */
    private PlaylistResponse toPlaylistResponse(Playlist playlist) {
        PlaylistResponse dto = new PlaylistResponse();
        dto.setId(playlist.getId());
        dto.setTitle(playlist.getTitle());
        dto.setDescription(playlist.getDescription());
        dto.setImageUrlP(playlist.getImageUrlP());
        return dto;
    }
}