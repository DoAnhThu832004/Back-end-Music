# Task List — Hệ Thống Gợi Ý Nhạc

## Entity Layer
- [x] Cập nhật `User.java` — thêm `preferredGenres` (ManyToMany → Genre)
- [x] Tạo mới `SongSimilarity.java` — entity cho bảng `song_similarity`

## Repository Layer
- [x] Tạo mới `SongSimilarityRepository.java`
- [x] Cập nhật `UserInteractionRepository.java` — thêm queries
- [x] Cập nhật `SongRepository.java` — thêm `findByIdIn`

## DTO Layer
- [x] Cập nhật `UserCreationRequest.java` — thêm `preferredGenreIds`
- [x] Cập nhật `UserUpdateRequest.java` — thêm `preferredGenreIds`
- [x] Cập nhật `UserResponse.java` — thêm `preferredGenres`
- [x] Tạo mới `RecommendationResponse.java`

## Service Layer — Offline
- [x] Viết lại `InteractionAggregationService.java` — toàn bộ pipeline offline

## Service Layer — Online
- [x] Viết lại `RecommendationEngineService.java` — toàn bộ pipeline online

## Service Layer — User
- [x] Cập nhật `UserService.java` — xử lý `preferredGenres` khi tạo user

## Controller Layer
- [x] Cập nhật `RecommendationController.java`
- [x] Cập nhật `RecommendationTriggerController.java`

## Configuration
- [x] Cập nhật `IdentityServiceApplication.java` — thêm `@EnableScheduling`
- [x] Cập nhật `UserMapper.java` — ignore `preferredGenres` khi map
