# Hệ Thống Gợi Ý Nhạc Dựa Trên Item-Based Collaborative Filtering

## Mô Tả Chung

Thay thế engine Apache Mahout hiện tại bằng một hệ thống tự triển khai hoàn toàn gồm 2 luồng:

- **Luồng Offline** (chạy 2h sáng): Tính toán ma trận độ tương đồng giữa các bài hát (Adjusted Cosine Similarity) và lưu vào bảng `song_similarity`.
- **Luồng Online** (real-time API): Dùng dữ liệu đã tính sẵn để trả về danh sách gợi ý trong < 50ms, có xử lý Cold Start và MMR Re-ranking.

---

## Phân Tích Hiện Trạng

| Thành phần | Hiện trạng | Hành động |
|---|---|---|
| `UserInteraction` entity | Đã có (user, song, ratingScore, interactionType) | Tái sử dụng |
| `InteractionAggregationService` | Chỉ sync từ Favorites (LIKE=5đ), thiếu PLAY, DOWNLOAD | **Cập nhật toàn bộ** |
| `RecommendationEngineService` | Dùng Apache Mahout, tính online = chậm | **Thay thế hoàn toàn** |
| `RecommendationController` | GET `/api/recommendations/users/{userId}` | **Cập nhật** |
| `RecommendationTriggerController` | POST `/api/admin/recommendations/trigger-aggregation` | **Cập nhật** |
| `SongRepository` | Có `findTop10ByOrderByPlayCountDesc()`, `findTop10ByGenres_IdOrderByPlayCountDesc()` | Tái sử dụng |
| `User` entity | **Không có trường `preferredGenres`** | **Cần khảo sát** |

> [!WARNING]
> Entity `User` hiện tại **không có** trường `preferredGenres` (thể loại yêu thích khi đăng ký). Phần Cold Start cho genre-based sẽ fallback về Global Trending nếu user chưa có genre preference. Có thể bổ sung sau.

> [!IMPORTANT]
> Apache Mahout (`mahout-mr`) sẽ **không bị xóa khỏi pom.xml** để tránh break compile — dependency này vẫn còn đó nhưng code sẽ không gọi đến nữa. `RecommendationEngineService` cũ sẽ bị thay thế.

---

## Proposed Changes

### 1. Entity Layer

#### [NEW] `SongSimilarity.java`
Entity ánh xạ bảng `song_similarity` với các cột:
- `songA_id` (FK → songs)
- `songB_id` (FK → songs)
- `similarityScore` (Double)
- `commonUserCount` (Integer) — số người nghe chung
- `updatedAt` (LocalDateTime)

Composite key: `(songA_id, songB_id)`

---

### 2. Repository Layer

#### [NEW] `SongSimilarityRepository.java`
```java
// Query các bài tương tự với bài đã nghe, loại trừ bài đã nghe
List<SongSimilarity> findBySongAIdAndSimilarityScoreGreaterThan(String songAId, double threshold);

// Dùng cho Batch Insert - xóa toàn bộ trước khi tính lại
void deleteAll();

// Native query để bulk insert hiệu quả
@Modifying
@Query(nativeQuery=true, value="INSERT INTO song_similarity ...")
void bulkInsert(...)
```

#### [MODIFY] `UserInteractionRepository.java`
Thêm query cho aggregation:
```java
// Lấy tất cả interaction gom nhóm theo user + song (tổng điểm)
@Query("SELECT ui.user.id, ui.song.id, SUM(ui.ratingScore) FROM UserInteraction ui GROUP BY ui.user.id, ui.song.id")
List<Object[]> findAggregatedScores();

// Kiểm tra user có interaction nào không
boolean existsByUserId(String userId);

// Lấy tất cả interaction của user (dùng cho online scoring)
List<UserInteraction> findAllByUserId(String userId);
```

#### [MODIFY] `SongRepository.java`
Thêm query cho Cold Start:
```java
// Global trending (đã có: findTop10ByOrderByPlayCountDesc)
// Genre trending (đã có: findTop10ByGenres_IdOrderByPlayCountDesc)
// Thêm: lấy bài hát theo list ID (dùng cho scoring)
List<Song> findByIdIn(List<String> ids);
```

---

### 3. Service Layer — Luồng Offline

#### [MODIFY] `InteractionAggregationService.java` — **Rebuild hoàn toàn**

**Bước 1.1 — Trigger:**
```java
@Scheduled(cron = "0 0 2 * * ?")  // 2h sáng mỗi ngày
public void runNightlyRecommendationJob()
```

**Bước 1.2 — Aggregation `aggregateScores()`:**
- Lấy tất cả `UserInteraction` từ DB
- Tính điểm theo loại: `PLAY=1, LIKE=3, DOWNLOAD=5`
- Gom nhóm theo `(userId, songId)` → tổng điểm

**Bước 1.3 — Mean-Centering:**
- Tính điểm trung bình mỗi user
- Điểm chuẩn hóa = điểm tổng - trung bình user
- Output: `Map<String, Map<String, Double>>` — userMeanCenteredMatrix

**Bước 1.4 — Adjusted Cosine Similarity:**
- Lấy danh sách tất cả songId
- Vòng lặp lồng nhau: với mỗi cặp (songA, songB)
  - Tìm tập users đã nghe **cả hai** bài
  - Nếu số user chung < 3 → bỏ qua (threshold)
  - Tính Adjusted Cosine: Σ(rA_u * rB_u) / (||rA|| * ||rB||) với r là mean-centered scores
  - Nếu score > 0.1 → lưu vào danh sách

**Bước 1.5 — Batch Insert:**
- Xóa toàn bộ bảng `song_similarity`
- Insert từng cụm 1000 records bằng `saveAll()` + `flush()`

#### [NEW] `SimilarityComputationService.java`
Tách riêng logic tính toán thuần túy (không phụ thuộc Spring) để dễ test:
- `Map<String, Double> computeMeanCenteredRatings(Map<String, Map<String, Double>> matrix)`
- `double computeAdjustedCosine(String songA, String songB, Map<...> matrix)`

---

### 4. Service Layer — Luồng Online

#### [MODIFY] `RecommendationEngineService.java` — **Rebuild hoàn toàn**

**Bước 2.1-2.2 — Cold Start Check:**
```java
public List<SongResponse> getRecommendations(String userId, int limit) {
    boolean hasHistory = userInteractionRepository.existsByUserId(userId);
    if (!hasHistory) {
        return handleColdStart(userId, limit);
    }
    return getPersonalizedRecommendations(userId, limit);
}
```

`handleColdStart()`:
- Truy vấn genres của user (nếu có) → `findTop10ByGenres_IdOrderByPlayCountDesc()`
- Fallback: `findTop10ByOrderByPlayCountDesc()` (Global Trending)

**Bước 2.3 — User Profile:**
```java
List<UserInteraction> interactions = userInteractionRepository.findAllByUserId(userId);
// → Map<songId, aggregatedScore>
```

**Bước 2.4 — Candidate Generation:**
```java
// Với mỗi bài đã nghe, lấy bài tương tự từ song_similarity
// Loại bỏ các bài user đã nghe rồi
Set<String> listenedSongs = ...;
List<SongSimilarity> candidates = songSimilarityRepository
    .findTopSimilarSongsExcluding(listenedSongIds, limit * 3);
```

**Bước 2.5 — Predicted Score:**
```
predictedScore(candidateSong) = Σ (userScore[listenedSong] × similarity[listenedSong][candidateSong])
                                   / Σ similarity[listenedSong][candidateSong]
```

**Bước 2.6 — MMR Re-ranking:**
```
MMR(d) = λ × relevance(d) - (1-λ) × max_similarity_to_already_selected(d)
λ = 0.7 (70% relevance, 30% diversity)
```
- Sử dụng genre overlap để đo similarity giữa các candidate
- Chọn lần lượt top K bài có MMR score cao nhất

**Bước 2.7 — Response:** Trả về `List<SongResponse>`

---

### 5. Controller Layer

#### [MODIFY] `RecommendationController.java`
Đổi endpoint thành:
```
GET /api/recommendations?userId={userId}&limit={limit}
```
(Đổi từ path variable sang query param cho chuẩn hơn với spec yêu cầu)

#### [MODIFY] `RecommendationTriggerController.java`
Thêm endpoint trigger thủ công cho similarity job:
```
POST /api/admin/recommendations/trigger-similarity
```

---

### 6. DTO Layer

#### [NEW] `RecommendationResponse.java`
```java
public class RecommendationResponse {
    private String source;    // "PERSONALIZED" | "COLD_START_GENRE" | "COLD_START_GLOBAL"  
    private int totalCount;
    private List<SongResponse> songs;
}
```

---

### 7. Configuration

#### [MODIFY] `IdentityServiceApplication.java`
Thêm `@EnableScheduling` để kích hoạt Cron job.

#### [MODIFY] `SecurityConfig.java`
Mở public endpoint cho recommendation API (nếu cần không-auth):
```java
"/api/recommendations/**"
```

---

## Cấu Trúc Bảng CSDL Mới

```sql
CREATE TABLE song_similarity (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    song_a_id   VARCHAR(36) NOT NULL,
    song_b_id   VARCHAR(36) NOT NULL,
    similarity_score  DOUBLE NOT NULL,
    common_user_count INT NOT NULL,
    updated_at  DATETIME,
    UNIQUE KEY uq_song_pair (song_a_id, song_b_id),
    INDEX idx_song_a (song_a_id),
    INDEX idx_song_b (song_b_id)
);
```
*Hibernate sẽ tự tạo bảng này do `ddl-auto: update`*

---

## Verification Plan

### Automated
1. `mvn compile` — đảm bảo không lỗi build
2. Kiểm tra log khi start: `@EnableScheduling` active

### Manual API Testing (Postman)
1. **Trigger job thủ công:**
   ```
   POST /identity/api/admin/recommendations/trigger-aggregation
   POST /identity/api/admin/recommendations/trigger-similarity
   ```
2. **Cold Start (user mới):**
   ```
   GET /identity/api/recommendations?userId=new-user-id&limit=10
   → Phải trả về Global Trending
   ```
3. **Personalized (user có lịch sử):**
   ```
   GET /identity/api/recommendations?userId=existing-user-id&limit=10
   → Phải trả về danh sách đa dạng, không trùng bài đã nghe
   ```
4. Kiểm tra bảng `song_similarity` có dữ liệu sau khi trigger

---

## Open Questions

> [!IMPORTANT]
> **Q1: Điểm số cho hành động PLAY?** Hiện tại `UserInteraction` có `interactionType` nhưng `InteractionAggregationService` chỉ sync từ Favorites. Tôi sẽ áp dụng:
> - `PLAY = 1 điểm`
> - `LIKE = 3 điểm` (từ bảng Favorites)
> - `DOWNLOAD = 5 điểm` (từ bảng DownloadedSong)
> Bạn có đồng ý với thang điểm này không?

> [!IMPORTANT]
> **Q2: User có `preferredGenres` không?** Entity `User` hiện tại không có trường này. Nếu bạn muốn Cold Start theo genre đã chọn lúc đăng ký, cần thêm quan hệ `User ↔ Genre`. Tạm thời tôi sẽ **fallback toàn bộ về Global Trending** cho Cold Start.

> [!NOTE]
> **Q3: Endpoint recommendation có cần xác thực (JWT)?** Hiện tại mọi endpoint đều yêu cầu auth. Tôi sẽ giữ nguyên yêu cầu JWT cho recommendation endpoint.
