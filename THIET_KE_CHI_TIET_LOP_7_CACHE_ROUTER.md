# 📐 BẢN ĐẶC TẢ THIẾT KẾ KỸ THUẬT: LỚP 7 (CACHE & MODEL ROUTER)
> **Dự án:** `identity-service-main` (Spring Boot 3 + Google Gemini) & Android Client App  
> **Chuyên đề:** Kiến trúc tối ưu hóa độ trễ, chi phí token và định tuyến mô hình  
> **Trạng thái:** 🚀 *Tài liệu đặc tả kỹ thuật hoàn thiện (Hiệu chuẩn lần 17 — Nghiệm thu Giai đoạn 1 & Giai đoạn 2 đạt 75% Code-Complete, Telemetry Instrumented, 109/109 Unit & Integration tests passed 100%, bảo chứng thực nghiệm 58 ca FastPathEngine bao phủ phủ định không dấu, chuẩn hóa Single Source of Truth liên kết Kế hoạch, làm rõ cơ chế SO_TIMEOUT socket 15s không trả byte nào vs cancel(true) luồng ảo, khắc phục PromQL alert increase() 5m, đo lường p95/p99 TTFT cho Gemini Pro Thinking, thay thế Redis SET NX cho đa instance)*

---

## 📑 MỤC LỤC
1. [Đánh Giá Hiện Trạng & Phân Rã Điểm Số Chuẩn Xác (75% Code-Complete, Telemetry Instrumented)](#1-đánh-giá-hiện-trạng--phân-rã-điểm-số-chuẩn-xác-75-code-complete-telemetry-instrumented)
2. [Sơ Đồ Luồng Xử Lý Chuẩn (Execution Pipeline)](#2-sơ-đồ-luồng-xử-lý-chuẩn-execution-pipeline)
3. [Module 1: Thiết Kế Fast-Path Engine & Đồng Bộ Hậu Giai Đoạn 1 [ĐÃ HOÀN THÀNH 100%]](#3-module-1-thiết-kế-fast-path-engine--đồng-bộ-hậu-giai-đoạn-1-đã-hoàn-thành-100)
   - [3.1. Phân biệt lệnh Player, Ngữ cảnh Turn 1, Chuẩn hóa Persona & Phản hồi trung tính cho `NEXT`](#31-phân-biệt-lệnh-player-ngữ-cảnh-turn-1-chuẩn-hóa-persona--phản-hồi-trung-tính-cho-next)
   - [3.2. Đàm phán Khả năng Client & Đồng bộ Android Client](#32-đàm-phán-khả-năng-client--đồng-bộ-android-client)
   - [3.3. Tách biệt Luồng Cleanup, Bẫy JPA Auditing, Migration DATETIME(6) & Tính Đối Ngẫu Tie-Breaker (String Lexicographical)](#33-tách-biệt-luồng-cleanup-bẫy-jpa-auditing-migration-datetime6--tính-đối-ngẫu-tie-breaker-string-lexicographical)
   - [3.4. Mã nguồn chuẩn xác Two-Tier `FastPathResult` & `FastPathEngine`](#34-mã-nguồn-chuẩn-xác-two-tier-fastpathresult--fastpathengine)
   - [3.5. Phân Tích Bề Mặt Khớp Của Tier 2 & Chiến Lược Phòng Thủ Phủ Định Không Dấu](#35-phân-tích-bề-mặt-khớp-của-tier-2--chiến-lược-phòng-thủ-phủ-định-không-dấu)
4. [Module 2: Thiết Kế Model Router & Resilience4j Circuit Breaker [ĐÃ HOÀN THÀNH 100%]](#4-module-2-thiết-kế-model-router--resilience4j-circuit-breaker-đã-hoàn-thành-100)
   - [4.1. Dependency, Phân cấp `UpstreamAiException` & Cấu hình Breaker `geminiCriticRouter` (Loại trừ 400/403)](#41-dependency-phân-cấp-upstreamaiexception--cấu-hình-breaker-geminicriticrouter-loại-trừ-400403)
   - [4.2. Điều kiện phân làn, Tách cờ `degradedByBreaker` & Đối tượng `RoutingDecision`](#42-điều-kiện-phân-làn-tách-cờ-degradedbybreaker--đối-tượng-routingdecision)
   - [4.3. Vòng đời Permit, First-Token Watchdog 8000ms (Phân biệt Socket 15s vs 90s, Safety 200 vs 400) & Tinh chỉnh Fallback Guard](#43-vòng-đời-permit-first-token-watchdog-8000ms-phân-biệt-socket-15s-vs-90s-safety-200-vs-400--tinh-chỉnh-fallback-guard)
5. [Module 3: CanCacheEvaluator, Telemetry Service & Khung Quyết Định Dữ Liệu [HOÀN TẤT KỸ THUẬT]](#5-module-3-cancacheevaluator-telemetry-service--khung-quyết-định-dữ-liệu-hoàn-tất-kỹ-thuật)
   - [5.1. Mã nguồn hoàn chỉnh `CanCacheEvaluator`: Cờ `(?U)` & Giảm thiểu False-Positive (Over-Bypass có chủ ý, 8 Lý do Bypass)](#51-mã-nguồn-hoàn-chỉnh-cancacheevaluator-cờ-u--giảm-thiểu-false-positive-over-bypass-có-chủ-ý-8-lý-do-bypass)
   - [5.2. Telemetry First: Mã nguồn `TelemetryService` (Caffeine 50k, 30m & 24h, Cô lập Persona & User ẩn danh)](#52-telemetry-first-mã-nguồn-telemetryservice-caffeine-50k-30m--24h-cô-lập-persona--user-ẩn-danh)
   - [5.3. Khung Quyết Định Kiến Trúc Dựa Trên Dữ Liệu 3 Nhánh & Bản chất Tỷ lệ Lặp](#53-khung-quyết-định-kiến-trúc-dựa-trên-dữ-liệu-3-nhánh--bản-chất-tỷ-lệ-lặp)
   - [5.4. Đặc tả Tương lai: Hệ Thống 5 Cổng So Khớp & Event-Driven Invalidation](#54-đặc-tả-tương-lai-hệ-thống-5-cổng-so-khớp--event-driven-invalidation)
6. [Module 4: Tích Hợp Controller & Orchestrator](#6-module-4-tích-hợp-controller--orchestrator)
   - [6.1. Triển khai `AiChatController` & Hàm `handleFastPathExecution`](#61-triển-khai-aichatcontroller--hàm-handlefastpathexecution)
   - [6.2. Cấu trúc `AgentTurnResult`](#62-cấu-trúc-agentturnresult)
   - [6.3. Mã nguồn tích hợp luồng điều phối chính trong `AiAgentOrchestrator`](#63-mã-nguồn-tích-hợp-luồng-điều-phối-chính-trong-aiagentorchestrator)
7. [Module 5: Kết Quả Kiểm Thử Thực Nghiệm & Metrics](#7-module-5-kết-quả-kiểm-thử-thực-nghiệm--metrics)
   - [7.1. Kết quả kiểm thử thực tế (109/109 Unit & Integration Tests Passed 100%)](#71-kết-quả-kiểm-thử-thực-tế-109109-unit--integration-tests-passed-100)
   - [7.2. Chỉ số Giám sát Micrometer Metrics & Cảnh Báo Prometheus](#72-chỉ-số-giám-sát-micrometer-metrics--cảnh-báo-prometheus)
8. [Phân Kỳ Triển Khai & Nghiệm Thu (Khớp Single Source of Truth)](#8-phân-kỳ-triển-khai--nghiệm-thu-khớp-single-source-of-truth)
9. [Nhật Ký Thay Đổi (Changelog)](#9-nhật-ký-thay-đổi-changelog)

---

## 1. ĐÁNH GIÁ HIỆN TRẠNG & PHÂN RÃ ĐIỂM SỐ CHUẨN XÁC (75% CODE-COMPLETE, TELEMETRY INSTRUMENTED)

> [!IMPORTANT]
> **Quy Chuẩn Nguồn Sự Thật Duy Nhất (Single Source of Truth):**  
> Toàn bộ bảng phân rã tiến độ 4 thành phần kỹ thuật (mỗi phần 25%), ma trận đối chuẩn chi tiết 109 tests và danh mục công việc được duy trì duy nhất tại tài liệu chuẩn: **[KE_HOACH_LOP_7_CACHE_ROUTER.md](KE_HOACH_LOP_7_CACHE_ROUTER.md)**. Tài liệu thiết kế này tập trung toàn bộ vào đặc tả kiến trúc kỹ thuật sâu, sơ đồ luồng và mã nguồn thực thi.

Tiến độ hoàn thiện của Lớp 7 được theo dõi dựa trên 4 thành phần kỹ thuật độc lập (mỗi thành phần chiếm trọng số 25%):

| Thành phần kỹ thuật | Trọng số | Trạng thái đạt được | Chi tiết kỹ thuật & Hiện trạng kiểm thử |
| :--- | :---: | :---: | :--- |
| **Traditional Data Cache** | 25% | **25% (Sẵn có)** | Caffeine cache cho top trending SQL, In-memory ConcurrentMap cho Vector bài hát |
| **Fast-Path Command Engine** | 25% | **+25% (Lũy kế 50%)** | **ĐÃ HOÀN THÀNH & NGHIỆM THU (67 tests):** Regex Two-Tier có/không dấu, unaccented fallback, "bài tiếp theo", chạy trước Session Lock, đồng bộ Android Client, `DATETIME(6)` microsecond precision, `@Transactional saveFastPathMessages`, Dual Tie-Breaker theo String, 5 ca test phủ định không dấu |
| **Dynamic Model Router & Fallback** | 25% | **+25% (Lũy kế 75%)** | **ĐÃ HOÀN THÀNH & NGHIỆM THU (17 tests):** `ModelRouterService`, Resilience4j `geminiCriticRouter` (loại trừ lỗi 400/403 safety khỏi Breaker), watchdog 8000ms trên `aiStreamingExecutor` (socket 15s), phân cấp `UpstreamAiException`, fallback an toàn phân biệt read-only vs side-effect |
| **Telemetry & Data-Driven Decision** | 25% | **Hạ tầng hoàn tất (25 tests)<br/>Đang thu thập số liệu** | **ĐÃ TRIỂN KHAI HOÀN TẤT & TEST (25 tests):** `CanCacheEvaluator` (8 cổng bypass, regex `(?U)`, giảm thiểu false-positive qua over-bypass "nhạc của anh" có chủ ý), `TelemetryService` (Caffeine 50k, cửa sổ 30m & 24h, cô lập `persona:canonical`, tách riêng anonymous prompts), `VietnameseTextNormalizer.stripDiacritics`. **100% Production-Ready** sẽ được ký duyệt sau khi hoàn tất đo đạc thực tế |
| **TỔNG CỘNG** | **100%** | **75% Code-Complete, Telemetry Instrumented** | **109/109 Unit & Integration Tests Passed (100% Pass Rate). Sẵn sàng giai đoạn đo lường thực tế.** |

---

## 2. SƠ ĐỒ LUỒNG XỬ LÝ CHUẨN (EXECUTION PIPELINE)

```mermaid
flowchart TD
    Req["👤 Request từ Client (rawPrompt, persona, sessionId, currentSongId, clientSupportsTransportActions)"] --> Step1["Bước 1: Security & Rate Limit (Lớp 5, 6)"]
    
    Step1 --> StepHistoryCheck["Đọc trạng thái lịch sử (existsBySessionId) - Non-blocking ngoài Lock"]
    
    StepHistoryCheck --> Step2{"Bước 2: Fast-Path Engine (Trước Session Lock)<br/>- fast-path-enabled == true?<br/>- Template chào hỏi khi hasPriorHistory == false<br/>- Lệnh Player khi currentSongId != null & clientSupportsTransportActions"}
    
    Step2 -- "Khớp Fast-Path" --> FastRet["🚀 Fast-Path Response (< 10ms) [ĐÃ HOÀN THÀNH]<br/>- Ghi DB cặp User & AI Message (+5ms bảo toàn qua DATETIME(6))<br/>- Stream SSE chunk & Done event<br/>- Cập nhật tiêu đề session (nếu Turn 1)<br/>- Cleanup riêng: KHÔNG giải phóng Session Lock"]
    
    Step2 -- "Không khớp / Không hỗ trợ" --> Step3["Bước 3: Lấy Session Lock & Nạp Lịch Sử Trượt<br/>- aiChatGuardService.checkAndLockSession(sessionId)<br/>- Nạp getSlidingWindowHistory(sessionId) với Tie-breaker đối ngẫu"]
    
    Step3 --> Step4["Bước 4: Can-Cache Evaluator & Telemetry (100% Request)<br/>- Quét 2 tầng (Có dấu & Không dấu) phân loại 8 lý do BYPASS<br/>- Ghi nhận Counter eligibility (true/false) & bypasses (reason)<br/>- Lưu sha256(persona:canonical) vào Caffeine 30m & 24h đo exact_repeat vs self_repeat"]
    
    Step4 -- "Cacheable (Turn 1 độc lập & currentSongId == null)" --> Step5{"Bước 5: Semantic Cache Lookup [Dự kiến - Chưa kích hoạt]<br/>(Chỉ chạy khi mode != OFF)"}
    
    Step5 -- "HIT (Đạt 5 Cổng So Khớp)" --> CacheRet["⚡ Cache Response (~150-300ms)<br/>- Trả kết quả (totalTokens = 0)<br/>- Caffeine getIfPresent<br/>- Callback lưu DB & Mở khóa Session"]
    
    Step5 -- "MISS" --> Step6["Bước 6: Dynamic Model Router<br/>(Làn A: Fast Tier vs Làn B: Reasoning Tier + Breaker)"]
    Step4 -- "Bypass (Có history / currentSongId != null / PII / Personal...)" --> Step6
    
    Step6 --> Step7["Bước 7: Multi-Step Agent Execution Loop<br/>- Quản lý Permit tại executeAgentLoop (chống double-release)<br/>- First-Token Watchdog 8000ms trên aiStreamingExecutor (socket 15s)<br/>- Fallback an toàn: Cho phép khi read-only tools, chặn khi side-effect tools"]
    
    Step7 --> Step8{"Bước 8: Kiểm Tra & Lưu Cache Hậu Kỳ [Dự kiến - Chưa kích hoạt]<br/>- evalResult.scope() == GENERAL?<br/>- precomputedEmbedding != null?<br/>- !usedPersonalTool?<br/>- !usedTool('get_top_trending_songs')?<br/>- !hasPriorHistory?<br/>- !wasFallbackTriggered?<br/>- !degradedByBreaker?<br/>- playerAction == null?<br/>- playlistProposal == null?<br/>- currentSongId == null?"}
    
    Step8 -- "Đủ điều kiện" --> SaveCache["💾 Lưu vào Semantic Cache (W-TinyLFU)"]
    Step8 -- "Không đủ điều kiện" --> SkipCache["Bỏ qua lưu Cache (dữ liệu cá nhân, lỗi thời hoặc side-effect)"]
    
    SaveCache --> StreamOut["📱 Stream SSE phản hồi về Client & Mở khóa Session"]
    SkipCache --> StreamOut
    FastRet --> StreamOut
    CacheRet --> StreamOut
```

---

## 3. MODULE 1: THIẾT KẾ FAST-PATH ENGINE & ĐỒNG BỘ HẬU GIAI ĐOẠN 1 [ĐÃ HOÀN THÀNH 100%]

### 3.1. Phân biệt lệnh Player, Ngữ cảnh Turn 1, Chuẩn hóa Persona & Phản hồi trung tính cho `NEXT`

1. **Vị trí trước Session Lock:**
   * Trong phương thức `AiChatController.streamChat`, lệnh `checkAndLockSession(session.getId())` được dùng để chống spam song song trên cùng một phiên. Nếu người dùng gửi lệnh *"tạm dừng"* trong lúc bot đang stream một câu trả lời dài, request sẽ bị quăng ngoại lệ `AI_SERVER_BUSY` (Session Locked) và không thể tạm dừng phát nhạc. Do đó, `FastPathEngine.evaluate` được thực thi **trước `checkAndLockSession`**.
2. **Đọc `hasPriorHistory` trước Session Lock & Đảm bảo Hiệu năng Index DB:**
   * `AiChatController` gọi truy vấn non-blocking `chatMessageRepository.existsBySessionId(session.getId())` để xác định Turn 1 mà không cần giữ Lock.
   * Bảng `chat_message` đã có sẵn composite index: `@Index(name = "idx_chat_message_session_created", columnList = "session_id, createdAt ASC")`. Do `session_id` là Leading Column, truy vấn thực hiện **Index Seek** trực tiếp tại RAM buffer cache với độ trễ $< 0.1\text{ms}$.
3. **Phân biệt lệnh Player và ngữ cảnh đàm thoại (`currentSongId != null`):**
   * Lệnh Player chỉ được FastPathEngine thụ lý khi `currentSongId != null` (đang có bài hát phát trên client). Nếu `currentSongId == null`, các câu như *"tiếp theo"* hay *"bài hát tiếp theo"* được phân loại là câu hỏi đàm thoại hoặc quy chiếu rỗng và đẩy xuống LLM.
4. **Phản hồi trung tính cho lệnh `NEXT`:**
   * Đối với `PAUSE` và `RESUME`, trạng thái thay đổi tức thì trên bài hát hiện tại nên bot phản hồi: *"Đã tạm dừng phát nhạc."* hoặc *"Đã tiếp tục phát nhạc."*.
   * Đối với `NEXT`, client là bên quyết định bài hát tiếp theo dựa trên hàng đợi phát (Queue/Shuffle). Bot không thể biết trước tên bài hát sắp phát nên sử dụng câu phản hồi trung tính chuẩn xác:
     ```java
     "Đã gửi lệnh chuyển bài."
     ```
5. **Chuẩn hóa Danh xưng & Tên Enum Persona (`AiPersona.HEALING_FRIEND`):**
   * Hệ thống thống nhất toàn diện enum `com.devteria.identityservice.enums.AiPersona` với 3 giá trị: `GENZ_DJ`, `MUSIC_CRITIC`, `HEALING_FRIEND` (thay thế dứt điểm tên nháp cũ `HEALING_BUDDY` để khớp 100% với mã nguồn Java).
   * Văn phong câu chào Turn 1 và câu giới thiệu danh tính được tinh chỉnh sắc thái: `MUSIC_CRITIC` dùng ngôi xưng *"Kính chào quý thính giả..."* (học thuật, trang trọng), `HEALING_FRIEND` dùng *"Chào bạn, mình luôn ở đây để lắng nghe..."* (ấm áp, vỗ về).

---

### 3.2. Đàm phán Khả năng Client & Đồng bộ Android Client

1. **Cờ `clientSupportsTransportActions` & Phòng Ngừa Unboxing NPE:**
   * Cờ boolean `clientSupportsTransportActions` trong `AiChatStreamRequest` là kiểu `Boolean` (đối tượng). Để tránh lỗi `NullPointerException` khi client cũ gửi giá trị null, Backend luôn kiểm tra qua:
     ```java
     boolean clientSupportsTransport = Boolean.TRUE.equals(request.clientSupportsTransportActions());
     ```
2. **Đồng bộ Android Client (`AgenticPlayerDispatcher.kt`):**
   * Trong `AgenticPlayerDispatcher.kt`, logic điều khiển nhạc đã được cấu trúc lại để chấp nhận thực thi ngay cả khi `songCard == null`:
     ```kotlin
     when (actionDto.action.uppercase()) {
         "PAUSE" -> {
             playerManager.pause()
         }
         "RESUME" -> {
             playerManager.resume()
         }
         "NEXT" -> {
             playerManager.next()
         }
         "PLAY" -> {
             val songCard = actionDto.song ?: return@collectLatest
             val song = songCard.toSong()
             if (actionDto.autoExecute) {
                 playerManager.play(song)
             }
         }
     }
     ```
   * Thay đổi này đã được commit (`407244d`) và đẩy lên nhánh `main` của client.

---

### 3.3. Tách biệt Luồng Cleanup, Bẫy JPA Auditing, Migration DATETIME(6) & Tính Đối Ngẫu Tie-Breaker (String Lexicographical)

1. **Tách biệt Luồng Cleanup Emitter:**
   * Luồng SSE của Fast-Path kết thúc ngay lập tức sau 2 chunk (`delta` và `done`). Luồng dọn dẹp riêng của Fast-Path chỉ gọi `fastEmitter.complete()`, tuyệt đối **không gọi `aiChatGuardService.releaseSessionLock(sessionId)`**, tránh giải phóng nhầm Lock của một stream Agentic khác đang chạy song song.
2. **Triệt tiêu Direct Field Access của JPA Auditing:**
   * `ChatMessage` kế thừa từ `AbstractAuditEntity`. Khi dùng `@CreatedDate`, Spring Data reflection sẽ chọc thẳng vào trường private lớp cha và ghi đè mất khoảng lệch `+5ms`.
   * **Giải pháp:** Chuyển `AbstractAuditEntity.java` sang dùng `@PrePersist onPrePersist()` và `@PreUpdate onPreUpdate()` thuần của JPA (kiểm tra `if (this.createdAt == null)`).
3. **Migration Flyway `V5` - Nâng cấp Microsecond Precision & Bảo Toàn `ON UPDATE`:**
   * Trong MySQL, việc chạy `MODIFY COLUMN` nếu không khai báo lại `ON UPDATE` sẽ làm mất cơ chế cập nhật tự động của cột `updated_at`. Do đó, migration Flyway `V5__alter_chat_tables_datetime_precision.sql` được viết chuẩn xác:
     ```sql
     ALTER TABLE chat_session
         MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
         MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

     ALTER TABLE chat_message
         MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
         MODIFY COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);
     ```
4. **Phương thức `@Transactional saveFastPathMessages`:**
   * Trong `AiChatSessionService.java`, gom việc lưu `userMessage` và `aiMessage` vào cùng một transaction duy nhất để đảm bảo tính toàn vẹn ACID:
     ```java
     @Transactional
     public void saveFastPathMessages(ChatMessage userMessage, ChatMessage aiMessage) {
         chatMessageRepository.save(userMessage);
         chatMessageRepository.save(aiMessage);
     }
     ```
5. **Tính Đối Ngẫu Tie-Breaker cho Thứ Tự Tin Nhắn (Chuẩn hóa Lexicographical theo String `sender` & MySQL Collation):**
   * Do UUIDv4 không có tính thứ tự thời gian, việc sử dụng `id` làm tie-breaker có thể làm xáo trộn thứ tự tin nhắn.
   * `ChatMessage` sử dụng `@Enumerated(EnumType.STRING)` trên cột `sender VARCHAR(16)`, lưu trực tiếp chuỗi ký tự `'USER'` và `'AI'`.
   * SQL Engine (MySQL) sắp xếp cột chuỗi theo **Thứ tự Bảng chữ cái (Lexicographical / Alphabetical order)** trong bảng mã ASCII/Unicode:
     - Ký tự `'U'` (ASCII 85) đứng sau ký tự `'A'` (ASCII 65), nghĩa là trong SQL: `'USER' > 'AI'`.
     - **Truy vấn xuôi (ASC - Hiển thị UI ban đầu):** `ORDER BY m.createdAt ASC, m.sender DESC` $\rightarrow$ Khi hai tin nhắn có cùng microsecond, `'USER'` ('U') sẽ được xếp TRƯỚC `'AI'` ('A').
     - **Truy vấn ngược (DESC - Lấy Sliding Window History):** `ORDER BY m.createdAt DESC, m.sender ASC` $\rightarrow$ Khi hai tin nhắn có cùng microsecond, `'AI'` ('A') xếp trước `'USER'` ('U'). Khi tầng ứng dụng gọi `Collections.reverse(list)`, thứ tự được đảo ngược hoàn hảo: `USER` luôn đứng trước `AI`.
   * **Bảo toàn trên MySQL Collation & Kế hoạch Kiểm thử Tích hợp:**
     - Cột `sender VARCHAR(16)` sử dụng collation chuẩn (ví dụ `utf8mb4_0900_ai_ci` hoặc `utf8mb4_unicode_ci`). Trong hầu hết các collation không phân biệt hoa thường (`_ci`) phổ biến nhất của MySQL, trọng số sắp xếp của `'U'` (kể cả hoa hay thường) luôn lớn hơn `'A'`, đảm bảo `'USER' > 'AI'`.
     - Để bảo chứng 100% trên DB engine thực tế thay vì chỉ suy luận lý thuyết ASCII, checklist đưa vào kế hoạch bài test tích hợp `AiChatRepositoryIntegrationTest` chạy với Testcontainers MySQL thật.

---

### 3.4. Mã nguồn chuẩn xác Two-Tier `FastPathResult` & `FastPathEngine`

Đây là mã nguồn thực tế đang chạy và được kiểm thử thành công trong `FastPathEngine.java`:

```java
package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.FastPathResult;
import com.devteria.identityservice.enums.AiPersona;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FastPathEngine {

    public static final class ActionType {
        public static final String PAUSE = "PAUSE";
        public static final String RESUME = "RESUME";
        public static final String NEXT = "NEXT";
        public static final String PLAY = "PLAY";
        public static final String QUEUE = "QUEUE";
    }

    // Regex có dấu (Tier 1) - Chỉ nhận khi currentSongId != null
    private static final Pattern STOP_ACCENTED = Pattern.compile(
            "^(?:tạm dừng|dừng|tắt|dừng lại|ngừng phát)(?: nhạc)?(?: (?:đi|nhé|giúp|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_ACCENTED = Pattern.compile(
            "^(?:tiếp tục|phát tiếp)(?: nhạc)?(?: (?:đi|nhé|giúp|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_ACCENTED = Pattern.compile(
            "^(?:bài tiếp|qua bài|chuyển bài|next bài|bài sau|bài kế tiếp|bài tiếp theo)(?: (?:đi|nhé|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);

    // Regex không dấu (Tier 2) - Phân nhóm dứt khoát, an toàn cho "tam dung nhac", "tat", "tat nhac"
    private static final Pattern STOP_UNACCENTED = Pattern.compile(
            "^(?:tam dung(?: nhac)?|tat(?: nhac)?|dung lai|ngung phat|stop|pause)(?: (?:di|nhe|giup|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_UNACCENTED = Pattern.compile(
            "^(?:tiep tuc(?: nhac)?|phat tiep(?: nhac)?|resume)(?: (?:di|nhe|giup|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_UNACCENTED = Pattern.compile(
            "^(?:bai tiep|next|qua bai|chuyen bai|next bai|bai sau|bai ke tiep|bai tiep theo)(?: (?:di|nhe|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);

    // Khớp chào hỏi và danh tính kèm các từ đệm thông dụng
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(?:chào|chao|hi|hello|xin chào|xin chao|helo|chào bạn|chao ban|chào bot|chao bot|hai bot|hai ban)(?: (?:nhé|nhe|ạ|a|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTITY_PATTERN = Pattern.compile(
            "^(?:bạn là ai|ban la ai|bạn tên gì|ban ten gi|tên bạn là gì|ten ban la gi|giới thiệu về bạn|gioi thieu ve ban)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");
    private static final Pattern VIETNAMESE_DIACRITICS = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            Pattern.CASE_INSENSITIVE);

    private FastPathEngine() {}

    public static Optional<FastPathResult> evaluate(
            String rawPrompt,
            AiPersona persona,
            String currentSongId,
            boolean hasPriorHistory,
            boolean clientSupportsTransportActions) {

        if (rawPrompt == null || rawPrompt.isBlank()) {
            return Optional.empty();
        }

        // 1. Chuẩn hóa NFC, gộp khoảng trắng và cắt dấu câu cuối
        String nfcPrompt = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String singleSpaced = nfcPrompt.replaceAll("\\s+", " ");
        String lowerRaw = TRAILING_PUNCTUATION.matcher(singleSpaced.toLowerCase(Locale.ROOT)).replaceAll("").trim();

        // 2. Chặn triệt để các câu phủ định có dấu ("đừng", "không", "chớ")
        // Lưu ý: Không kiểm tra từ không dấu "dung" để tránh chặn nhầm lệnh "tạm dừng" / "dừng lại"
        if (lowerRaw.startsWith("đừng ") || lowerRaw.startsWith("không ") || lowerRaw.startsWith("chớ ")
                || lowerRaw.contains(" đừng ") || lowerRaw.contains(" không ") || lowerRaw.contains(" chớ ")
                || lowerRaw.equals("đừng") || lowerRaw.equals("không") || lowerRaw.equals("chớ")) {
            return Optional.empty();
        }

        // 3. Giới hạn độ dài an toàn và chặn câu truy vấn ca sĩ / bài hát
        if (lowerRaw.length() > 60 || lowerRaw.contains(" ca khúc ") || lowerRaw.contains(" ca sĩ ") || lowerRaw.contains(" của ")) {
            return Optional.empty();
        }

        // 4. Khớp câu chào hỏi / danh tính theo Persona (Chỉ áp dụng khi hasPriorHistory == false)
        if (!hasPriorHistory) {
            if (IDENTITY_PATTERN.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.textMessage(resolveIdentityResponse(persona)));
            }
            if (GREETING_PATTERN.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.textMessage(resolveGreetingResponse(persona)));
            }
        }

        // 5. Khớp lệnh Player Control: Phải có bài đang phát & client hỗ trợ
        if (currentSongId == null || currentSongId.isBlank() || !clientSupportsTransportActions) {
            return Optional.empty();
        }

        // Kiến trúc Two-Tier: Phân biệt rõ chuỗi có dấu vs không dấu
        boolean hasDiacritics = VIETNAMESE_DIACRITICS.matcher(lowerRaw).find();

        if (hasDiacritics) {
            if (STOP_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.PAUSE, "Đã tạm dừng phát nhạc."));
            }
            if (RESUME_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.RESUME, "Đã tiếp tục phát nhạc."));
            }
            if (NEXT_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.NEXT, "Đã gửi lệnh chuyển bài."));
            }
        }

        // Tier 2: Khớp không dấu (áp dụng cho câu không dấu hoặc câu gõ nửa nạc nửa mỡ không khớp Tier 1)
        String unaccented = hasDiacritics ? VietnameseTextNormalizer.stripDiacritics(lowerRaw) : lowerRaw;
        if (STOP_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.PAUSE, "Đã tạm dừng phát nhạc."));
        }
        if (RESUME_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.RESUME, "Đã tiếp tục phát nhạc."));
        }
        if (NEXT_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.NEXT, "Đã gửi lệnh chuyển bài."));
        }

        return Optional.empty();
    }

    private static String resolveGreetingResponse(AiPersona persona) {
        return switch (persona != null ? persona : AiPersona.GENZ_DJ) {
            case GENZ_DJ -> "Yo! GenZ DJ đây, hôm nay bạn muốn lên dây cót tinh thần bằng giai điệu nào? Bật mí ngay nhé!";
            case MUSIC_CRITIC -> "Kính chào quý thính giả. Hôm nay chúng ta cùng khám phá và thưởng thức tác phẩm âm nhạc nào?";
            case HEALING_FRIEND -> "Chào bạn, mình luôn ở đây để lắng nghe bạn. Hôm nay tâm trạng của bạn cảm thấy thế nào?";
        };
    }

    private static String resolveIdentityResponse(AiPersona persona) {
        return switch (persona != null ? persona : AiPersona.GENZ_DJ) {
            case GENZ_DJ -> "Mình là GenZ DJ - trợ lý âm nhạc AI siêu nhiệt huyết của bạn! Mình có thể bật nhạc, mix playlist và trò chuyện cực cuốn.";
            case MUSIC_CRITIC -> "Tôi là Chuyên gia Phê bình Âm nhạc (Music Critic). Tôi đồng hành cùng quý vị để phân tích cấu trúc, hòa âm và giá trị nghệ thuật tác phẩm.";
            case HEALING_FRIEND -> "Mình là Người bạn Chữa lành (Healing Friend). Mình ở đây để lắng nghe tâm sự và xoa dịu tâm hồn bạn bằng những giai điệu bình yên.";
        };
    }
}
```

---

### 3.5. Phân Tích Bề Mặt Khớp Của Tier 2 & Chiến Lược Phòng Thủ Phủ Định Không Dấu

Việc bổ sung cơ chế fallback không dấu cho chuỗi có dấu (`hasDiacritics == true`) giúp người dùng gõ lỗi dấu (ví dụ *"tạm dung"*, *"bài tiep theo"*) vẫn kích hoạt Fast-Path mượt mà. Tuy nhiên, việc mở rộng bề mặt so khớp này cần được phân tích thực nghiệm cẩn trọng:

1. **Kiểm chứng thực tế: Liệu câu phủ định không dấu (`dung tat nhac`, `ko dung lai`, `cho tat`) có bị khớp nhầm không?**
   - Xem xét biểu thức chính quy Tier 2 đang chạy trong mã nguồn `FastPathEngine.java`:
     ```java
     private static final Pattern STOP_UNACCENTED = Pattern.compile(
         "^(?:tam dung(?: nhac)?|tat(?: nhac)?|dung lai|ngung phat|stop|pause)(?: (?:di|nhe|giup|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
         Pattern.CASE_INSENSITIVE);
     ```
   - Do regex sử dụng cấu trúc **neo cứng đầu chuỗi `^` và cuối chuỗi `$`**:
     - Câu `"dung tat nhac"` (đừng tắt nhạc): Bắt đầu bằng tiền tố `"dung tat"`, **HOÀN TOÀN KHÔNG KHỚP** `STOP_UNACCENTED`.
     - Câu `"ko dung lai"` (không dừng lại): Bắt đầu bằng tiền tố `"ko"`, **HOÀN TOÀN KHÔNG KHỚP** `STOP_UNACCENTED`.
     - Câu `"cho tat nhac"` (chớ tắt nhạc): Bắt đầu bằng tiền tố `"cho"`, **HOÀN TOÀN KHÔNG KHỚP** `STOP_UNACCENTED`.
     - Câu `"ko tat"`: Bắt đầu bằng tiền tố `"ko"`, **HOÀN TOÀN KHÔNG KHỚP** `STOP_UNACCENTED`.
     - Câu `"dung tam dung"` (đừng tạm dừng): Bắt đầu bằng tiền tố `"dung tam"`, **HOÀN TOÀN KHÔNG KHỚP** `STOP_UNACCENTED`.
   - **Bảo chứng bằng Kiểm thử Thực nghiệm (58/58 Tests PASS):** Cả 5 ca phủ định không dấu trên (`"dung tat nhac"`, `"ko dung lai"`, `"cho tat nhac"`, `"ko tat"`, `"dung tam dung"`) đã được đưa trực tiếp vào bộ kiểm thử tham số hóa `@ParameterizedTest` trong `FastPathEngineTest.java`. Kết quả chạy thực tế: **58/58 ca kiểm thử PASS 100%**, tất cả đều trả về `Optional.empty()` và chuyển tiếp an toàn xuống Agentic Loop của LLM.
   - **Kết luận thực nghiệm:** Rủi ro kích hoạt ngoài ý muốn trên mã nguồn hiện tại là **RẤT THẤP**, việc so khớp được bảo vệ vững chắc bởi neo `^` và `$` của regex.

2. **Cảnh báo bẫy thiết kế & Sự nhập nhằng ngữ nghĩa của từ không dấu:**
   - Trong tiếng Việt không dấu, từ `"dung"` là từ đồng âm đa nghĩa: vừa có nghĩa là **"dừng"** (dừng lại - lệnh Player hợp lệ), vừa có nghĩa là **"đừng"** (phủ định).
   - Nếu áp dụng giải pháp phỏng đoán sơ sài như `unaccented.startsWith("dung ")`, hệ thống sẽ **CHẶN NHẦM CHÍNH LỆNH HỢP LỆ** của người dùng như `"dung lai"`, `"dung nhac di"`!
   - Tương tự, các cụm từ mở rộng tiềm ẩn sự nhập nhằng ngữ nghĩa sâu sắc:
     - Cụm `"dung phat"`: Người dùng có thể có ý định **"đừng phát"** (phủ định - muốn LLM xử lý) HOẶC **"dừng phát"** (lệnh Player dừng nhạc).
     - Cụm `"dung tiep"`: Có thể hiểu là **"dừng tiếp"** (lệnh dừng bài tiếp theo) HOẶC **"đừng tiếp"** (phủ định "đừng tiếp tục").
   - Do đó, việc định tuyến các cụm từ nhập nhằng này không thể dựa vào phỏng đoán chủ quan, mà bắt buộc phải dựa trên phân tích log truy vấn người dùng thực tế trong giai đoạn Telemetry.

3. **Định hướng Kiến Trúc Phòng Thủ Chiều Sâu (Kiểm Chứng Bằng Test Suite):**
   - **Nguyên tắc kỹ thuật cốt lõi:** Tuyệt đối không thêm bộ lọc phủ định theo phỏng đoán. Mọi tinh chỉnh logic ở Bước 2 bắt buộc phải được thiết kế đi kèm bộ test case đối chuẩn tự động trước khi sửa mã nguồn.
   - Khi cần gia cố triệt để cho các nhánh regex lỏng lẻo trong tương lai, bộ lọc ngữ cảnh phải phân tách rõ ràng:
     - Phủ định rõ ràng không nhập nhằng: `^(?:ko|khong|cho dung|cho tat)\b`
     - Chặn `"dung"` **chỉ khi theo sau là động từ thao tác khác**: `^dung (?:tat|chuyen|phat|tiep)\b` (nghĩa là "đừng tắt", "đừng chuyển", "đừng phát").
     - Tuyệt đối bảo toàn nguyên vẹn các lệnh dừng hợp lệ: `^dung lai\b`, `^tam dung\b`, `^dung nhac\b`.

---

## 4. MODULE 2: THIẾT KẾ MODEL ROUTER & RESILIENCE4J CIRCUIT BREAKER [ĐÃ HOÀN THÀNH 100%]

### 4.1. Dependency, Phân cấp `UpstreamAiException` & Cấu hình Breaker `geminiCriticRouter` (Loại trừ 400/403)

1. **Phân cấp `UpstreamAiException`:**
   * Trong Resilience4j, nếu một ngoại lệ không nằm trong `record-exceptions` mà truyền vào `circuitBreaker.onError()`, Breaker sẽ coi đó là một lượt gọi **THÀNH CÔNG** (`onSuccess`).
   * **Giải pháp:** Định nghĩa lớp cơ sở `UpstreamAiException` và cho `FirstTokenTimeoutException` kế thừa từ lớp này. Khai báo `UpstreamAiException` vào danh sách `record-exceptions`.
2. **Loại trừ lỗi 400 Bad Request và 403 Forbidden khỏi Breaker:**
   * *Nguy cơ:* Nếu dùng `RestClientResponseException`, các lỗi HTTP 400 (Google từ chối vì vi phạm Safety Policy / Harmful Content, hoặc prompt của user không hợp lệ) sẽ bị tính là lỗi upstream. Với persona `MUSIC_CRITIC` (lưu lượng thấp, cửa sổ 120s tối thiểu 4 calls), chỉ cần 2 prompt bị safety block là Breaker bị nảy OPEN oan uổng!
   * *Khắc phục:* Thu hẹp `record-exceptions` chỉ ghi nhận 5xx, 429 và ResourceAccessException; đưa HTTP 400 và 403 vào `ignore-exceptions`.
3. **Cấu hình YAML chuẩn xác:**
   ```yaml
   ai:
     router:
       fast-path-enabled: ${AI_FAST_PATH_ENABLED:true}
       reasoning-tier-enabled: ${AI_REASONING_TIER_ENABLED:true}
       fast-tier-model: ${GEMINI_FAST_MODEL:${gemini.model:gemini-3.5-flash-lite}}
       reasoning-tier-model: ${GEMINI_REASONING_MODEL:gemini-2.5-pro}
       first-token-timeout-ms: 8000

   resilience4j:
     circuitbreaker:
       instances:
         geminiCriticRouter:
           sliding-window-type: TIME_BASED
           sliding-window-size: 120
           minimum-number-of-calls: 4
           failure-rate-threshold: 50.0
           wait-duration-in-open-state: 30s
           permitted-number-of-calls-in-half-open-state: 2
           record-exceptions:
             - com.devteria.identityservice.exception.UpstreamAiException
             - org.springframework.web.client.HttpServerErrorException
             - org.springframework.web.client.ResourceAccessException
             - org.springframework.web.client.HttpClientErrorException$TooManyRequests
           ignore-exceptions:
             - org.apache.catalina.connector.ClientAbortException
             - org.springframework.web.context.request.async.AsyncRequestNotUsableException
             - org.springframework.web.client.HttpClientErrorException$BadRequest
             - org.springframework.web.client.HttpClientErrorException$Forbidden
   ```

---

### 4.2. Điều kiện phân làn, Tách cờ `degradedByBreaker` & Đối tượng `RoutingDecision`

* **Điều kiện phân làn REASONING_TIER:**
  1. `reasoning-tier-enabled == true`.
  2. Persona là `AiPersona.MUSIC_CRITIC`.
  3. Prompt dài $> 40$ ký tự VÀ chứa ít nhất một từ khóa nhạc lý không dấu (ranh giới từ):
     `"hoa am"`, `"phoi khi"`, `"cau truc giai dieu"`, `"phan tich nghe thuat"`, `"thu phap sang tac"`, `"so sanh phong cach"`, `"vocal technique"`.
* **Tách bạch 2 cờ hạ cấp:**
  * `degradedByBreaker`: `true` khi Breaker OPEN $\rightarrow$ **CẤM lưu cache** (tránh lưu câu trả lời hạ cấp).
  * `reasoningTierDisabled`: `true` khi admin chủ động tắt cấu hình $\rightarrow$ **CHO PHÉP lưu cache**.

```java
public record RoutingDecision(
    ModelTier tier,
    String targetModel,
    boolean degradedByBreaker,
    boolean reasoningTierDisabled
) {}

public enum ModelTier {
    FAST_TIER,
    REASONING_TIER
}
```

---

### 4.3. Vòng đời Permit, First-Token Watchdog 8000ms (Phân biệt Socket 15s vs 90s, Safety 200 vs 400) & Tinh chỉnh Fallback Guard

1. **Vòng đời Permit & Quản lý Tài nguyên Semaphore:**
   * Trước khi gọi model Reasoning Tier, lấy permit qua `circuitBreaker.tryAcquirePermission()`. Nếu thất bại (Breaker OPEN), lập tức hạ cấp sang Flash và gán `degradedByBreaker = true`.
   * Sử dụng cờ `AtomicBoolean resultRecorded = new AtomicBoolean(false)` trong khối `finally` để đảm bảo permit được giải phóng (`releasePermission()`) hoặc ghi nhận lỗi (`onError()`) duy nhất một lần.
2. **First-Token Watchdog 8000ms & Phân Định Socket Timeout (15s vs 90s):**
   * Watchdog thực thi qua `CompletableFuture.supplyAsync(..., aiStreamingExecutor)` trên Virtual Threads với `get(8000, TimeUnit.MILLISECONDS)`.
   * **Bản chất kỹ thuật của `readTimeout = 15.000ms` & Endpoint Gọi Thực Tế:**
     - Trong Spring `SimpleClientHttpRequestFactory`, `readTimeout` là cấu hình `SO_TIMEOUT` trên Socket TCP — tức là **thời gian tối đa chờ giữa 2 gói tin (packet) liên tiếp**, không phải thời gian tính tổng toàn bộ request.
     - Trong kiến trúc Multi-Step Agent hiện tại (`AiAgentOrchestrator.java`), các iteration của vòng lặp gọi endpoint `/models/{model}:generateContent` dạng **đồng bộ non-streaming (Block REST call)** để bóc tách JSON cấu trúc Function Calling một cách chuẩn xác. Do không streaming, server chỉ trả về toàn bộ payload sau khi hoàn tất. Với `maxOutputTokens = 800`, mô hình Pro thường sinh xong trong 2–4 giây; `readTimeout = 15s` là trần thời gian tối đa cho toàn bộ phản hồi của một iteration.
     - **Khi Watchdog kích hoạt (8000ms):**
       - Lệnh `future.cancel(true)` giải phóng luồng ứng dụng đang chờ đợi và ngắt luồng gọi, cho phép trả về kết quả fallback cho người dùng ngay lập tức.
       - **Cơ chế Socket và Thread bên dưới:** Với `SimpleClientHttpRequestFactory`, `future.cancel(true)` gửi interrupt đến luồng ảo, nhưng một thread đang bị chặn trong phương thức đọc socket I/O (`SocketInputStream.read`) sẽ **không thoát ra ngay lập tức** do `HttpURLConnection` không đóng socket khi thread bị interrupt. Luồng ảo này sẽ tiếp tục bị chặn cho đến khi `SO_TIMEOUT` (15s) xảy ra hoặc server gửi dữ liệu.
       - **Hệ quả thời gian socket tồn tại:** Socket của `geminiRestClient` (15s) sẽ tồn tại thêm **tối đa 7 giây (15s - 8s) trong trường hợp không có byte nào được server gửi về (0-byte starvation)**. Nếu server trả byte nhỏ giọt, socket có thể tồn tại lâu hơn 15s kể từ lúc bắt đầu đọc. Tuy nhiên, việc gán trần `readTimeout = 15s` đảm bảo kết nối ngầm không bị rò rỉ vô tận như khi dùng `geminiStreamRestClient` (90s).
   * **Độ trễ Gemini 2.5 Pro Thinking Budget & Hiệu chỉnh Ngưỡng Watchdog:**
     - Ngưỡng 8000ms hiện tại (`first-token-timeout-ms: 8000`) là **ngưỡng khởi điểm dự phòng (provisional threshold)**, không phải là một hằng số sản xuất bất biến.
     - Mô hình `gemini-2.5-pro` sở hữu cơ chế suy luận chuyên sâu (internal thinking budget). Đối với các câu hỏi nhạc lý phức tạp, mô hình có thể dành nhiều giây để "suy nghĩ" trước khi phát ra token đầu tiên, khiến TTFT (Time-To-First-Token) thực tế có thể vượt quá 8s mà không phải do lỗi mạng hay sự cố máy chủ.
     - Do đó, trong giai đoạn chạy thử nghiệm Staging/Production, đội ngũ vận hành bắt buộc phải giám sát phân phối độ trễ `ai.latency.ttft{model="gemini-2.5-pro"}` qua các bách phân vị **p50, p95 và p99** để tinh chỉnh ngưỡng Watchdog phù hợp (ví dụ nâng lên 10s - 12s nếu p95 thực tế là 9.5s), tránh việc Breaker bị kích hoạt nhầm khi mô hình đang xử lý bình thường.
   * **Kiến trúc Hai Giai Đoạn (Two-Phase Client Architecture) khi hỗ trợ Streaming cho Reasoning Tier:**
     - Nếu nâng cấp Reasoning Tier sang gọi trực tiếp `:streamGenerateContent` cho câu trả lời dài của Pro (nhiều tool, văn bản giải thích nhạc lý chuyên sâu):
       - *Giai đoạn 1 (First-Token Phase):* Giám sát thời gian chờ nhận **chunk đầu tiên** bằng Watchdog 8000ms qua client ngắn.
       - *Giai đoạn 2 (Streaming Phase):* Sau khi nhận được chunk đầu tiên (chứng minh server sẵn sàng), luồng stream chuyển sang đọc dữ liệu qua client dài (`readTimeout = 90s` hoặc `interChunkTimeout = 15s` giữa các packet) để hoàn tất stream văn bản dài mà không bị ngắt quãng giữa chừng.
3. **Xử Lý Safety Blocks (HTTP 200 vs HTTP 400) & Thiết Lập Alert Giám Sát Độc Lập:**
   * **Safety Block dạng HTTP 400 (Client Error):** Đã được đưa vào `ignore-exceptions` của Breaker để bảo vệ Circuit Breaker không bị nhảy OPEN oan uổng khi người dùng gửi prompt độc hại vi phạm Safety Policy.
   * **Safety Block dạng HTTP 200 (In-Stream Block):** Gemini có thể trả về HTTP 200 nhưng payload chứa `promptFeedback.blockReason` hoặc `candidates[0].finishReason == "SAFETY"`. Ở tầng mạng, Circuit Breaker ghi nhận đây là lượt gọi thành công (HTTP 200 OK) nên không tăng tỷ lệ lỗi hạ tầng — đây là hành vi thiết kế chuẩn mực.
   * **Khai báo Counter Metric & Giám sát cảnh báo độc lập (Prometheus Alerting):**
     - Hệ thống khai báo 2 counter độc lập: `ai.gemini.client.errors` (cho lỗi 400/403) và `ai.gemini.safety.blocks` (cho in-stream HTTP 200 safety blocks).
     - Thiết lập cảnh báo Prometheus kết hợp để không bỏ sót các cuộc tấn công Prompt Injection hàng loạt hoặc vi phạm API key:
       - **Sửa cú pháp PromQL Alert:** Hàm `rate()` đo lường số sự kiện trên mỗi giây (events per second). Với ứng dụng âm nhạc quy mô vừa và nhỏ, mức lỗi 5 lần trong 5 phút chỉ tương đương 5 / 300 = 0.0167 req/s, nên điều kiện `rate(...) > 5` sẽ không bao giờ được kích hoạt (cần tới 1.500 lỗi/5 phút mới đạt rate > 5!). Do đó, quy tắc cảnh báo chuẩn xác bắt buộc phải sử dụng hàm `increase(...[5m]) > 5` để đo số lượng sự kiện tuyệt đối gia tăng trong 5 phút (kèm giới hạn miền giá trị nhãn `reason` chống bùng nổ cardinality):
       ```promql
       # Cảnh báo Safety Blocks hoặc lỗi Client 400/403 tăng đột biến (> 5 sự kiện trong 5 phút)
       # Lưu ý: Ngưỡng > 5 là con số tham khảo khởi điểm, cần hiệu chỉnh (tune) theo baseline lưu lượng thực tế
       sum(increase(ai_gemini_client_errors_total{status=~"400|403"}[5m])) + sum(increase(ai_gemini_safety_blocks_total[5m])) > 5
       ```
4. **Tinh chỉnh Fallback Guard An Toàn:**
   * Các tool chỉ đọc (`search_music_library`, `get_top_trending_songs`, `get_user_recommendations`): Khi xảy ra lỗi trên Pro, **CHO PHÉP fallback sang Flash** để tổng hợp câu trả lời từ dữ liệu đã lấy.
   * Các tool có tác dụng phụ làm thay đổi trạng thái (`trigger_player_action`, `propose_ai_playlist`): **CHẶN ĐỨNG fallback tuyệt đối**, dừng an toàn và trả lỗi `AI_SERVER_BUSY` để chống lỗi Double Mutation (thực thi lệnh 2 lần).

---

## 5. MODULE 3: CANCHEEVALUATOR, TELEMETRY SERVICE & KHUNG QUYẾT ĐỊNH DỮ LIỆU [HOÀN TẤT KỸ THUẬT]

### 5.1. Mã nguồn hoàn chỉnh `CanCacheEvaluator`: Cờ `(?U)` & Giảm Thiểu False-Positive (Over-Bypass Có Chủ Ý, 8 Lý do Bypass)

Việc cập nhật pattern dữ liệu cá nhân sang `nhạc của (?:tôi|mình|em|anh)` giúp loại trừ triệt để việc bắt nhầm cụm danh từ *"nhạc Anh"* (nhạc nước Anh / UK music) thành sở thích cá nhân. Mặc dù câu chứa đại từ như *"nhạc của anh"* vẫn bị bypass, đây là **cơ chế Over-Bypass có chủ ý (Conservative Defense)**: Trong giao tiếp tiếng Việt, *"anh"* là đại từ nhân xưng rất phổ biến; việc đẩy câu hỏi cho LLM trả lời động luôn an toàn hơn việc lưu vào Cache dùng chung làm rò rỉ dữ liệu cá nhân.

```java
package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import com.devteria.identityservice.util.VietnameseTextNormalizer;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CanCacheEvaluator {

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");

    // 1. Dữ liệu cá nhân (Personal Data) - Quét có dấu (UNICODE (?U)) và không dấu
    // Bắt buộc có "của" cho đại từ để tránh bắt nhầm "nhạc Anh" (nhạc nước Anh / tiếng Anh) thành dữ liệu cá nhân
    private static final Pattern PERSONAL_ACCENTED = Pattern.compile(
            "(?U)\\b(?:của (?:tôi|mình|em|anh)|gu (?:của )?(?:tôi|mình|em|anh)|playlist (?:của )?(?:tôi|mình|em|anh)|danh sách (?:của )?(?:tôi|mình|em|anh)|nhạc của (?:tôi|mình|em|anh)|bài (?:hát )?(?:của )?(?:tôi|mình) (?:yêu )?thích|(?:tôi|mình) (?:hay nghe|yêu thích|thích|đã nghe)|lịch sử nghe)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERSONAL_UNACCENTED = Pattern.compile(
            "\\b(?:cua (?:toi|minh|em|anh)|gu (?:cua )?(?:toi|minh|em|anh)|playlist (?:cua )?(?:toi|minh|em|anh)|danh sach (?:cua )?(?:toi|minh|em|anh)|nhac cua (?:toi|minh|em|anh)|bai (?:hat )?(?:cua )?(?:toi|minh) (?:yeu )?thich|(?:toi|minh) (?:hay nghe|yeu thich|thich|da nghe)|lich su nghe)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 2. Thông tin nhận dạng cá nhân (PII) - Số điện thoại, Email, CCCD/CMND
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:0|\\+84)(?:[\\s.-]?[0-9]){9,10}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b\\d{9,12}\\b");

    // 3. Đại từ thay thế phụ thuộc ngữ cảnh (Anaphora)
    private static final Pattern ANAPHORA_ACCENTED = Pattern.compile(
            "(?U)\\b(?:bài này|bài đó|bài vừa rồi|bài trước|ca khúc này|ca khúc đó)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANAPHORA_UNACCENTED = Pattern.compile(
            "\\b(?:bai nay|bai do|bai vua roi|bai truoc|ca khuc nay|ca khuc do)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 4. Từ khóa thời gian thực (Temporal)
    private static final Pattern TEMPORAL_ACCENTED = Pattern.compile(
            "(?U)\\b(?:hôm nay|bây giờ|hiện tại|mới nhất|sáng nay|tối nay|tuần này|tháng này|năm nay)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TEMPORAL_UNACCENTED = Pattern.compile(
            "\\b(?:hom nay|bay gio|hien tai|moi nhat|sang nay|toi nay|tuan nay|thang nay|nam nay)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 5. Quy chiếu lửng lơ (Dangling References)
    private static final Pattern DANGLING_ACCENTED = Pattern.compile(
            "(?U)\\b(?:còn bài nào|bài tiếp theo|tiếp đi|nữa không|còn không)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DANGLING_UNACCENTED = Pattern.compile(
            "\\b(?:con bai nao|bai tiep theo|tiep di|nua khong|con khong)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public CacheEvaluationResult evaluate(String rawPrompt, String currentSongId, boolean hasPriorHistory) {
        if (rawPrompt == null || rawPrompt.trim().length() < 2) {
            return CacheEvaluationResult.bypass(CacheBypassReason.EMPTY_PROMPT);
        }

        if (hasPriorHistory) {
            return CacheEvaluationResult.bypass(CacheBypassReason.HISTORY);
        }

        if (currentSongId != null && !currentSongId.isBlank()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.HAS_SONG_CONTEXT);
        }

        String nfc = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String singleSpaced = nfc.replaceAll("\\s+", " ");
        String lowerAccented = TRAILING_PUNCTUATION.matcher(singleSpaced.toLowerCase(Locale.ROOT)).replaceAll("").trim();
        String lowerUnaccented = VietnameseTextNormalizer.stripDiacritics(lowerAccented);

        if (PHONE_PATTERN.matcher(lowerAccented).find()
                || EMAIL_PATTERN.matcher(lowerAccented).find()
                || ID_CARD_PATTERN.matcher(lowerAccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.PII);
        }

        if (PERSONAL_ACCENTED.matcher(lowerAccented).find()
                || PERSONAL_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.PERSONAL);
        }

        if (ANAPHORA_ACCENTED.matcher(lowerAccented).find()
                || ANAPHORA_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.ANAPHORA);
        }

        if (TEMPORAL_ACCENTED.matcher(lowerAccented).find()
                || TEMPORAL_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.TEMPORAL);
        }

        if (DANGLING_ACCENTED.matcher(lowerAccented).find()
                || DANGLING_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.DANGLING_REF);
        }

        return CacheEvaluationResult.general();
    }
}
```

---

### 5.2. Telemetry First: Mã nguồn `TelemetryService` (Caffeine 50k, 30m & 24h, Cô lập Persona & User ẩn danh)

```java
package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import com.devteria.identityservice.util.VietnameseTextNormalizer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
@Slf4j
public class TelemetryService {

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private final MeterRegistry meterRegistry;
    // Lưu key: sha256(persona:canonicalPrompt), value: userId
    private final Cache<String, String> repeat30mCache;
    private final Cache<String, String> repeat24hCache;

    public TelemetryService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.repeat30mCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
        this.repeat24hCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();
    }

    public void recordPromptTelemetry(String userId, String rawPrompt, CacheEvaluationResult evalResult) {
        recordPromptTelemetry(userId, rawPrompt, null, evalResult);
    }

    public void recordPromptTelemetry(String userId, String rawPrompt, AiPersona persona, CacheEvaluationResult evalResult) {
        try {
            if (meterRegistry == null || evalResult == null) {
                return;
            }

            if (evalResult.scope() == CacheScope.BYPASS) {
                meterRegistry.counter("ai.cache.semantic.eligibility", "eligible", "false").increment();
                String reasonTag = evalResult.bypassReason() != null ? evalResult.bypassReason().name() : "UNKNOWN";
                meterRegistry.counter("ai.cache.semantic.bypasses", "reason", reasonTag).increment();
                return;
            }

            meterRegistry.counter("ai.cache.semantic.eligibility", "eligible", "true").increment();

            if (rawPrompt == null || rawPrompt.isBlank()) {
                return;
            }

            // Tách riêng người dùng ẩn danh: không đưa vào repeat cache để bảo toàn độ sạch của tỷ lệ liên người dùng
            if (userId == null || userId.isBlank()) {
                meterRegistry.counter("ai.cache.anonymous_prompts").increment();
                return;
            }

            String canonical = toCanonicalPrompt(rawPrompt);
            String personaPrefix = (persona != null) ? persona.name() : "GLOBAL";
            String cacheKey = personaPrefix + ":" + canonical;
            String sha256 = computeSha256(cacheKey);

            // 1. Cửa sổ 30 phút
            String existingUser30m = repeat30mCache.getIfPresent(sha256);
            if (existingUser30m != null) {
                if (existingUser30m.equals(userId)) {
                    meterRegistry.counter("ai.cache.self_repeat_hits").increment();
                } else {
                    meterRegistry.counter("ai.cache.exact_repeat_hits").increment();
                }
            } else {
                repeat30mCache.put(sha256, userId);
            }

            // 2. Cửa sổ 24 giờ (đo lường hit rate tiềm năng thực tế của Cache)
            String existingUser24h = repeat24hCache.getIfPresent(sha256);
            if (existingUser24h != null) {
                if (existingUser24h.equals(userId)) {
                    meterRegistry.counter("ai.cache.self_repeat_24h_hits").increment();
                } else {
                    meterRegistry.counter("ai.cache.exact_repeat_24h_hits").increment();
                }
            } else {
                repeat24hCache.put(sha256, userId);
            }
        } catch (Exception e) {
            log.warn("[Telemetry] Fail-safe: Lỗi khi ghi nhận telemetry: {}", e.getMessage());
        }
    }

    public String toCanonicalPrompt(String rawPrompt) {
        if (rawPrompt == null) return "";
        String nfc = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String unaccented = VietnameseTextNormalizer.stripDiacritics(nfc);
        String noPunct = TRAILING_PUNCTUATION.matcher(unaccented).replaceAll("");
        String singleSpaced = MULTI_WHITESPACE.matcher(noPunct).replaceAll(" ");
        return singleSpaced.trim().toLowerCase(Locale.ROOT);
    }

    public String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    public long getCacheSize() {
        return repeat30mCache.estimatedSize();
    }

    public long getCache24hSize() {
        return repeat24hCache.estimatedSize();
    }

    public void clearCache() {
        repeat30mCache.invalidateAll();
        repeat24hCache.invalidateAll();
    }
}
```

---

### 5.3. Khung Quyết Định Kiến Trúc Dựa Trên Dữ Liệu 3 Nhánh & Bản Chất Tỷ Lệ Lặp

Để tránh bẫy "ngụy biện năng lực" (chưa thể tuyên bố 100% Production-Ready khi hệ thống chưa chạy thực tế với dữ liệu người dùng thật và chưa kiểm thử tải), Giai đoạn 3 áp dụng phương pháp thống kê khoa học:

1. **Cỡ mẫu & Cửa sổ quan sát:**
   - Cần thu thập cỡ mẫu tối thiểu **$N \ge 5.000$ lượt hợp lệ** (hoặc tối thiểu 3.000 lượt trên Staging/Production trong 14 ngày) để bảo đảm lực thống kê (Statistical Power) cho việc phân biệt ngưỡng 1% và 3% (tránh khoảng tin cậy 95% quá rộng khi $N=1.000$).
   - Đo lường đồng thời trên 2 cửa sổ: **30 phút** (đo lặp lại tức thời) và **24 giờ** (phản ánh tuổi thọ lưu cache thực tế trong ngày).
   - Tích hợp `AiPersona` vào khóa băm SHA-256 (`persona:canonical`) nhằm đảm bảo dữ liệu đo lường không bị thổi phồng bởi các câu hỏi giống nhau ở các persona khác nhau.
   - Cô lập người dùng ẩn danh bằng metric `ai.cache.anonymous_prompts`, loại trừ khỏi mẫu tính tỷ lệ lặp lại liên người dùng để số liệu phản ánh chính xác 100%.

2. **Công thức Đo lường & Định nghĩa Bản chất Metric:**
   $$\text{Tỷ lệ Prompt Lặp lại (Repeat Ratio - 24h)} = \frac{\text{ai.cache.exact\_repeat\_24h\_hits}}{\text{ai.cache.semantic.eligibility(eligible=true)} - \text{ai.cache.anonymous\_prompts}} \times 100\%$$

   $$\text{Tỷ lệ Prompt Lặp lại (Repeat Ratio - 30m)} = \frac{\text{ai.cache.exact\_repeat\_hits}}{\text{ai.cache.semantic.eligibility(eligible=true)} - \text{ai.cache.anonymous\_prompts}} \times 100\%$$

   > [!NOTE]
   > **Phân biệt ý nghĩa trên Dashboard:**
   > - Lần đầu tiên một câu hỏi xuất hiện ($k=1$), đó là câu hỏi mới (New Prompt / Cache MISS). Chỉ từ lần xuất hiện thứ hai trở đi ($k \ge 2$), mỗi lượt xuất hiện mới được tính là một hit lặp lại (`exact_repeat_hits++`).
   > - Do đó, công thức trên phản ánh **"Tỷ lệ prompt đến hệ thống là bản sao lặp lại"**, tương đương với **"Potential Cache Hit Rate" (Tỷ lệ Cache Hit tiềm năng có thể đạt được)** nếu có cache. Nó KHÔNG phải là hit rate của một cache đang vận hành thực tế.
   > - *Ghi chú về trường hợp biên Prompt rỗng:* Khi `rawPrompt` trống hoặc $< 2$ ký tự, `CanCacheEvaluator` trả về `EMPTY_PROMPT` và được ghi nhận vào `eligibility(eligible=false)`. Trong trường hợp request không có định danh người dùng, mã nguồn trả về sớm trước khi đếm `anonymous_prompts`. Sai lệch biên này là cực kỳ nhỏ ($< 0.01\%$) và hoàn toàn không làm sai lệch ý nghĩa của Potential Cache Hit Rate.

3. **Bản chất Cận Dưới (Lower Bound) & Điều Kiện Ràng Buộc Cho Nhánh 3:**
   - Exact-Repeat Telemetry chỉ bắt các câu hỏi trùng khớp từng chữ sau khi chuẩn hóa canonical. Semantic Cache thật sự có thể bắt được các câu diễn đạt khác nhau (Paraphrasing, e.g. *"gợi ý nhạc buồn"* vs *"tìm bài hát tâm trạng đêm mưa"*).
   - Vì vậy, con số đo từ Exact Match 24h là **Cận Dưới Tuyệt Đối (Strict Lower Bound)** của tiềm năng Semantic Cache.
   - **Quy tắc nghiêm ngặt cho Nhánh 3 (`< 1%`):** **KHÔNG ĐƯỢC VỘI VÃ KÝ DUYỆT BỎ CACHE** chỉ dựa trên con số Exact-Repeat. Hệ thống bắt buộc phải trải qua bước kiểm chứng ngữ nghĩa: Chạy **Shadow Mode trên 5–10% lưu lượng** hoặc tính toán khoảng cách cosine trên embedding ngầm của một tập mẫu ngẫu nhiên. Chỉ khi tỷ lệ tương đồng ngữ nghĩa ($\cos \ge 0.92$) cũng đo được $< 1\%$ thì mới chính thức ký duyệt loại bỏ Semantic Cache để tiết kiệm RAM/CPU.

4. **Cơ Chế Đào Thải W-TinyLFU & Đặc Tính In-Memory Single-Instance:**
   - **Eviction khi vượt quá 50.000 mục:** Cache Caffeine 24h được cấp phát `maximumSize(50_000)`. Nếu lưu lượng prompt hợp lệ trong ngày vượt quá 50.000 mục, thuật toán W-TinyLFU sẽ loại bỏ (evict) các câu hỏi ít lặp lại trước hạn. Điều này khiến một số câu hỏi lặp lại sau đó bị ghi nhận như câu hỏi mới, làm giảm nhẹ số đo hit rate. Tuy nhiên, điều này hoàn toàn nhất quán với định hướng: **Exact Repeat Telemetry là một cận dưới thận trọng (Conservative Lower Bound)**.
   - **Phạm vi Đo lường Single-Instance:** Bộ đếm hiện tại chạy hoàn toàn trong RAM của từng JVM instance độc lập. Khi hệ thống mở rộng theo chiều ngang (Horizontal Scale-out) với nhiều pod/instance, câu hỏi lặp lại giữa các instance khác nhau sẽ không được tính chéo. Khi chuyển sang giai đoạn vận hành Production tải lớn với nhiều pod, hệ thống có thể tổng hợp metric toàn cụm qua Prometheus aggregation, hoặc thay thế bộ nhớ cục bộ bằng Redis phân tán với lệnh nguyên tử `SET telemetry:24h:{sha256} {userId} EX 86400 NX` (lệnh trả về false nếu key đã tồn tại $\rightarrow$ ghi nhận hit lặp lại chéo giữa các instance). Tuyệt đối không dùng Redis HyperLogLog cho tác vụ này vì HLL chỉ ước lượng lực lượng phân biệt ($E$), không hỗ trợ kiểm tra sự tồn tại (membership) của từng phần tử.

```mermaid
graph TD
    Data["📊 Thu Thập Telemetry Ngầm (N >= 5.000 lượt, 24h & 30m)"] --> Evaluate{"Đánh Giá Tỷ Lệ Lặp Độc Lập<br/>(exact_repeat_24h_hits)"}
    
    Evaluate -- ">= 3% (Tải lặp cao)" --> DeployCache["🚀 Triển khai Hệ Thống Vector Cache 5 Cổng<br/>(pgvector / Milvus / in-memory HNSW)<br/>Tiết kiệm chi phí token & Giảm tải LLM"]
    
    Evaluate -- "1% - 3% (Vùng giao thoa)" --> ShadowMode["🔬 Kích hoạt SHADOW MODE (10% Traffic trong 7 ngày)<br/>Đo lường độ trễ & Tiết kiệm token thực tế<br/>trước khi cấp phát RAM cho Vector DB"]
    
    Evaluate -- "< 1% (Cận dưới thấp)" --> SemanticVerify{"🔬 Kiểm Chứng Ngữ Nghĩa Ngầm<br/>(Shadow Mode hoặc Embedding Cosine Similarity tập mẫu)"}
    
    SemanticVerify -- "Semantic Repeat >= 1%" --> ShadowMode
    SemanticVerify -- "Semantic Repeat < 1%" --> SignOff["🏆 Ký Duyệt Nghiệm Thu 100% Production-Ready<br/>KHÔNG CẦN Semantic Cache (Pragmatic Architecture)<br/>Tiết kiệm RAM & CPU, bảo toàn hiệu năng tối ưu"]
```

---

### 5.4. Đặc tả Tương lai: Hệ Thống 5 Cổng So Khớp & Event-Driven Invalidation

Khi tỷ lệ lặp $\ge 3\%$ hoặc sau khi hoàn tất Shadow Mode, hệ thống kích hoạt triển khai 5 Cổng So Khớp:
1. **Cổng 1 (Persona Isolation):** Khóa tra cứu độc lập theo Persona (`AiPersona`).
2. **Cổng 2 (LSH / Pre-filtering Trie):** Thu hẹp không gian tìm kiếm qua cụm từ khóa âm nhạc.
3. **Cổng 3 (Cosine Similarity):** So khớp ngữ nghĩa vector $\cos(\vec{u}, \vec{v}) \ge \theta$ ($\theta = 0.92$).
4. **Cổng 4 (Entity Extraction Matching):** Khớp chính xác tên nghệ sĩ, bài hát được đề cập.
5. **Cổng 5 (Freshness & Availability Check):** Xác thực các bài hát trong câu trả lời vẫn đang `ACTIVE` trong hệ thống.

* **Cơ chế Vô hiệu hóa theo Sự kiện (`SongCatalogChangedEvent`):** Lắng nghe sự kiện thêm/sửa/xóa bài hát để invalidate các entry cache liên quan qua Spring `@EventListener`.

---

## 6. MODULE 4: TÍCH HỢP CONTROLLER & ORCHESTRATOR

### 6.1. Triển khai `AiChatController` & Hàm `handleFastPathExecution`

```java
// Trong AiChatController.java
boolean clientSupportsTransport = Boolean.TRUE.equals(request.clientSupportsTransportActions());

Optional<FastPathResult> fastResultOpt = FastPathEngine.evaluate(
        request.message(),
        session.getPersona(),
        request.currentSongId(),
        hasPriorHistory,
        clientSupportsTransport
);

if (fastResultOpt.isPresent()) {
    return handleFastPathExecution(session, currentUser, currentSong, fastResultOpt.get(), request, hasPriorHistory);
}

// Chỉ lấy Lock khi không khớp Fast-Path
aiChatGuardService.checkAndLockSession(session.getId());
```

```java
private SseEmitter handleFastPathExecution(
        ChatSession session,
        User currentUser,
        Song currentSong,
        FastPathResult fastResult,
        AiChatStreamRequest request,
        boolean hasPriorHistory) {

    Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;

    if (!hasPriorHistory) {
        aiChatSessionService.updateSessionTitleIfDefault(session.getId(), request.message());
    }

    LocalDateTime userTimestamp = LocalDateTime.now();
    ChatMessage userMessage = ChatMessage.builder()
            .session(session)
            .sender(MessageSender.USER)
            .status(MessageStatus.COMPLETE)
            .content(request.message().trim())
            .currentSong(currentSong)
            .tokenCount(0)
            .build();
    userMessage.setCreatedAt(userTimestamp);

    ChatMessage aiMessage = ChatMessage.builder()
            .session(session)
            .sender(MessageSender.AI)
            .status(MessageStatus.COMPLETE)
            .content(fastResult.responseText())
            .currentSong(currentSong)
            .tokenCount(0)
            .build();
    aiMessage.setCreatedAt(userTimestamp.plus(5, ChronoUnit.MILLIS));

    // Lưu DB an toàn trong 1 transaction duy nhất
    aiChatSessionService.saveFastPathMessages(userMessage, aiMessage);

    SseEmitter fastEmitter = new SseEmitter(10_000L);
    try {
        Map<String, Object> chunkPacket = Map.of(
                "delta", fastResult.responseText(),
                "done", false
        );
        fastEmitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunkPacket)));

        Map<String, Object> completePacket = new LinkedHashMap<>();
        completePacket.put("delta", "");
        completePacket.put("done", true);
        completePacket.put("status", "COMPLETE");
        completePacket.put("totalTokens", 0);
        completePacket.put("suggestedSongs", Collections.emptyList());
        completePacket.put("playerAction", fastResult.playerAction());
        completePacket.put("playlistProposal", null);

        fastEmitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(completePacket)));
        fastEmitter.complete();
    } catch (IOException e) {
        fastEmitter.completeWithError(e);
    } finally {
        if (sample != null && meterRegistry != null) {
            sample.stop(meterRegistry.timer("ai.latency.fastpath"));
            meterRegistry.counter("ai.fastpath.hits", "type", fastResult.type().name()).increment();
        }
    }
    return fastEmitter;
}
```

---

### 6.2. Cấu trúc `AgentTurnResult`

```java
public record AgentTurnResult(
        String fullText,
        int totalTokens,
        MessageStatus status,
        List<SongCardDto> suggestedSongs,
        PlayerActionDto playerAction,
        PlaylistProposalDto playlistProposal,
        String errorMessage,
        boolean usedPersonalTool,
        boolean wasFallbackTriggered,
        boolean degradedByBreaker,
        String actualModelUsed
) {}
```

---

### 6.3. Mã nguồn tích hợp luồng điều phối chính trong `AiAgentOrchestrator`

```java
// Trong AiAgentOrchestrator.java
aiStreamingExecutor.submit(() -> {
    // 0. Đánh giá khả năng Cache & Ghi nhận Telemetry (Bước đệm Giai đoạn 3)
    CacheEvaluationResult cacheEvalResult = null;
    if (canCacheEvaluator != null) {
        try {
            cacheEvalResult = canCacheEvaluator.evaluate(userRawPrompt, currentSongId, hasPriorHistory);
            if (telemetryService != null) {
                telemetryService.recordPromptTelemetry(currentUsername, userRawPrompt, currentPersona, cacheEvalResult);
            }
        } catch (Exception e) {
            log.warn("[Agent Orchestrator] Fail-safe: Lỗi khi đánh giá Cache / Telemetry: {}", e.getMessage());
        }
    }

    // 1. Phân làn mô hình (Routing Decision)
    RoutingDecision routingDecision = modelRouterService.resolveDecision(userRawPrompt, currentPersona);
    String currentTargetModel = routingDecision.targetModel();
    boolean degradedByBreaker = routingDecision.degradedByBreaker();
    ...
```

---

## 7. MODULE 5: KẾT QUẢ KIỂM THỬ THỰC NGHIỆM & METRICS

### 7.1. Kết quả kiểm thử thực tế (109/109 Unit & Integration Tests Passed 100%)

Toàn bộ hệ thống được kiểm chứng qua bộ kiểm thử tự động với Maven và JDK 21 (số học được đối soát chính xác 100%):

| Nhóm chức năng | Lớp Kiểm Thử (Test Class) | Số lượng test | Kết quả | Trọng tâm kiểm chứng |
| :--- | :--- | :---: | :---: | :--- |
| **Giai đoạn 1** | `FastPathEngineTest` | 58 | **58/58 PASS** | Regex Two-Tier có/không dấu, lệnh đệm dài, phản hồi trung tính NEXT, "bài tiếp theo", unaccented fallback, chặn phủ định (bao phủ 5 ca phủ định không dấu: "dung tat nhac", "ko dung lai", "cho tat nhac", "ko tat", "dung tam dung"), chống nhầm tên riêng |
| | `AiChatControllerTest` | 9 | **9/9 PASS** | Fast-Path Turn 1 & Turn 2+, Player Transport, Legacy client fallback, IDOR check, Session Lock isolation |
| **Giai đoạn 2** | `ModelRouterServiceTest` | 7 | **7/7 PASS** | Phân làn Critic với từ khóa nhạc lý không dấu, chặn prompt ngắn, hạ cấp khi Breaker OPEN |
| | `CircuitBreakerLifecycleTest` | 7 | **7/7 PASS** | Vòng đời Permit, Watchdog 8000ms trên aiStreamingExecutor, nhả permit khi client abort, an toàn fallback theo phân loại tool |
| | `AiAgentOrchestratorTest` | 3 | **3/3 PASS** | Vòng lặp Agentic loop, kiểm định anti-hallucination, xử lý fallback |
| **Giai đoạn 3** | `CanCacheEvaluatorTest` | 11 | **11/11 PASS** | Bao phủ 8 cổng BYPASS, câu GENERAL hợp lệ, over-bypass "nhạc Anh" có chủ ý |
| | `TelemetryServiceTest` | 8 | **8/8 PASS** | Canonical prompt SHA-256 kèm Persona prefix, đếm bypass reason, phân biệt cross-user vs self-retry (30m & 24h), cô lập anonymous prompts |
| | `VietnameseTextNormalizerTest` | 6 | **6/6 PASS** | Chuẩn hóa không dấu, xử lý ký tự Đ/đ, tách từ tokenize, bóc tách dấu giữ nguyên hoa thường (`stripDiacritics`) |
| **TỔNG CỘNG** | **8 test classes** | **109** | **109/109 PASS** | **Tỷ lệ vượt qua: 100% (BUILD SUCCESS)** |

---

### 7.2. Chỉ số Giám sát Micrometer Metrics & Cảnh Báo Prometheus

```java
// 1. Fast-Path & Circuit Breaker Metrics
meterRegistry.counter("ai.fastpath.hits", "type", type);
Timer.builder("ai.latency.fastpath").register(meterRegistry); // SLO < 10ms
meterRegistry.counter("ai.router.routing", "tier", tier.name(), "model", targetModel);
meterRegistry.counter("ai.router.fallbacks", "from", fromModel, "to", toModel);
Timer.builder("ai.latency.ttft").tag("model", targetModel).register(meterRegistry);
Gauge.builder("ai.router.circuitbreaker.state", circuitBreaker, cb -> cb.getState().getOrder()).register(meterRegistry);

// 2. Semantic Cache & Telemetry Metrics (30m, 24h & Anonymous)
meterRegistry.counter("ai.cache.semantic.eligibility", "eligible", "true/false");
meterRegistry.counter("ai.cache.semantic.bypasses", "reason", reason);
meterRegistry.counter("ai.cache.exact_repeat_hits");      // Lặp lại liên người dùng (30 phút)
meterRegistry.counter("ai.cache.self_repeat_hits");       // Cùng người dùng thử lại (30 phút)
meterRegistry.counter("ai.cache.exact_repeat_24h_hits");  // Lặp lại liên người dùng (24 giờ - Cận dưới Semantic Cache)
meterRegistry.counter("ai.cache.self_repeat_24h_hits");   // Cùng người dùng thử lại (24 giờ)
meterRegistry.counter("ai.cache.anonymous_prompts");      // Câu hỏi từ người dùng ẩn danh (cô lập khỏi cache)

// 3. Gemini Client & In-Stream Safety Metrics (Phục vụ Prometheus Alerting)
meterRegistry.counter("ai.gemini.client.errors", "status", String.valueOf(statusCode)); // 400, 403
meterRegistry.counter("ai.gemini.safety.blocks", "reason", blockReason, "type", blockType); // type="response" (finishReason=SAFETY) hoặc type="prompt" (blockReason)
```

```yaml
# Prometheus Alert Rule giám sát bất thường API Upstream và Safety Blocks
# Lưu ý: Ngưỡng > 5 trong 5m là giá trị tham khảo khởi điểm, cần hiệu chỉnh theo baseline lưu lượng thực tế
- alert: AiGeminiSafetyOrClientErrorsSpike
  expr: sum(increase(ai_gemini_client_errors_total{status=~"400|403"}[5m])) + sum(increase(ai_gemini_safety_blocks_total[5m])) > 5
  for: 2m
  labels:
    severity: warning
  annotations:
    summary: "Phát hiện đột biến lỗi client (400/403) hoặc in-stream safety blocks từ Gemini"
    description: "Số lỗi client hoặc safety blocks tăng cao bất thường (> 5 sự kiện trong 5 phút). Cần kiểm tra prompt injection hoặc sự cố API key."
```

---

## 8. PHÂN KỲ TRIỂN KHAI & NGHIỆM THU (KHỚP SINGLE SOURCE OF TRUTH)

> [!NOTE]
> **Nguồn Sự Thật Duy Nhất (Single Source of Truth):** Để loại bỏ hoàn toàn nguy cơ lệch thông tin giữa các tài liệu, tiến độ chi tiết, ma trận phân bổ 109 tests và checklist công việc thực tế được đồng bộ tuyệt đối theo tài liệu chuẩn: [KE_HOACH_LOP_7_CACHE_ROUTER.md](KE_HOACH_LOP_7_CACHE_ROUTER.md).

```
GIAI ĐOẠN 1: Fast-Path Engine & Unit Tests [✅ ĐÃ HOÀN THÀNH - ĐẠT 25% (Lũy kế 50%)]
  ├── [x] Tích hợp FastPathEngine trong AiChatController TRƯỚC dòng checkAndLockSession
  ├── [x] Định nghĩa FastPathResult, ActionType và phản hồi trung tính cho NEXT ("Đã gửi lệnh chuyển bài.")
  ├── [x] Kiểm tra tồn tại lịch sử nhẹ nhàng (existsBySessionId) ngoài Lock để phân định Turn 1
  ├── [x] Hỗ trợ cờ clientSupportsTransportActions kèm kiểm tra Boolean.TRUE.equals chống NPE
  ├── [x] Chuẩn hóa tên enum AiPersona.HEALING_FRIEND và văn phong chào hỏi / giới thiệu danh tính
  ├── [x] Thiết lập luồng dọn dẹp riêng cho Fast-Path Emitter (KHÔNG giải phóng Session Lock của stream khác)
  ├── [x] Cập nhật tiêu đề session khi là câu đầu tiên (updateSessionTitleIfDefault)
  ├── [x] Cập nhật Android Client: AiChatModels.kt và AgenticPlayerDispatcher.kt (hỗ trợ PAUSE/RESUME/NEXT khi song == null)
  ├── [x] Bổ sung biến thể "bài tiếp theo" / "bai tiep theo" và fallback cho câu gõ lẫn lộn dấu
  ├── [x] Viết FastPathEngineTest bao phủ 58 ca biên (58/58 PASS bao gồm 5 ca phủ định không dấu)
  ├── [x] Viết AiChatControllerTest bao phủ 9 ca kiểm thử tích hợp (9/9 PASS)
  └── [x] Nghiệm thu 67/67 tests với Maven và JDK 21 (BUILD SUCCESS)

HẬU GIAI ĐOẠN 1: Tinh chỉnh Database & Kích hoạt Metrics [✅ ĐÃ HOÀN THÀNH]
  ├── [x] Cập nhật AbstractAuditEntity: Dùng @PrePersist onPrePersist() bảo toàn createdAt khi đã có giá trị (+5ms)
  ├── [x] Tạo migration Flyway V5 nâng cấp created_at / updated_at sang DATETIME(6) kèm ON UPDATE CURRENT_TIMESTAMP(6)
  ├── [x] Sắp xếp đối ngẫu theo chuỗi Lexicographical: ORDER BY m.createdAt ASC, m.sender DESC và ORDER BY m.createdAt DESC, m.sender ASC
  ├── [x] Gom lưu DB vào phương thức @Transactional saveFastPathMessages trong AiChatSessionService
  └── [x] Gắn Timer.Sample (ai.latency.fastpath) và counter (ai.fastpath.hits) quanh handleFastPathExecution

GIAI ĐOẠN 2: Model Router & Circuit Breaker [✅ ĐÃ HOÀN THÀNH - ĐẠT 25% (Lũy kế 75% Code-Complete)]
  ├── [x] Khai báo io.github.resilience4j:resilience4j-spring-boot3:2.2.0 và spring-boot-starter-aop trong pom.xml
  ├── [x] Khai báo AiRouterProperties (ai.router.*) và cấu hình instance geminiCriticRouter trong application.yaml
  ├── [x] Loại trừ lỗi 400 (Safety block) và 403 khỏi record-exceptions để chống ngắt mạch oan
  ├── [ ] Thiết lập cảnh báo Prometheus Alert độc lập cho lỗi 400/403 và in-stream safety blocks (đã thiết kế rule PromQL increase(), chờ cấu hình hạ tầng)
  ├── [x] Xây dựng ModelRouterService sinh RoutingDecision độc lập (phân làn Music Critic với regex nhạc lý không dấu)
  ├── [x] Tách bạch 2 cờ hạ cấp: degradedByBreaker (chặn lưu cache) và reasoningTierDisabled (cho phép lưu cache)
  ├── [x] Quản lý Permit tại executeAgentLoop với cờ resultRecorded chống double release
  ├── [x] Áp dụng First-Token Watchdog 8000ms qua CompletableFuture trên aiStreamingExecutor; kết nối geminiRestClient (socket 15s)
  ├── [x] Tinh chỉnh fallback guard: Cho phép read-only tools, chặn đứng side-effect tools chống Double Mutation
  ├── [x] Định nghĩa phân cấp UpstreamAiException ghi nhận vào record-exceptions của Breaker
  ├── [x] Phân loại ngoại lệ theo nguồn: ClientAbortException (hủy upstream + nhả permit) vs Upstream error (ghi nhận vào Breaker)
  └── [x] Viết ModelRouterServiceTest (7/7 PASS), CircuitBreakerLifecycleTest (7/7 PASS), AiAgentOrchestratorTest (3/3 PASS)

GIAI ĐOẠN 3: Bước Đệm Telemetry & Khung Quyết Định Dữ Liệu [✅ HẠ TẦNG HOÀN TẤT - TIẾN TỚI 100% PRODUCTION-READY]
  ├── [x] Xây dựng CanCacheEvaluator quét 2 tầng có dấu ((?U)) và không dấu phân loại 8 lý do BYPASS
  ├── [x] Cơ chế Over-Bypass có chủ ý: Tách "nhạc của anh" khỏi "nhạc Anh" bảo vệ an toàn PII
  ├── [x] Xây dựng TelemetryService (Caffeine 50.000 mục, TTL 30m & 24h, canonicalization SHA-256 tích hợp Persona)
  ├── [x] Cô lập người dùng ẩn danh bằng metric ai.cache.anonymous_prompts, không làm ô nhiễm repeat cache
  ├── [x] Tách tiện ích dùng chung VietnameseTextNormalizer.stripDiacritics dùng cho cả 3 service
  ├── [x] Tích hợp CanCacheEvaluator và TelemetryService an toàn (fail-safe) vào AiAgentOrchestrator
  ├── [x] Viết CanCacheEvaluatorTest (11/11 PASS), TelemetryServiceTest (8/8 PASS), VietnameseTextNormalizerTest (6/6 PASS)
  ├── [ ] Thu thập số liệu telemetry thực tế trên production/staging (Mẫu N >= 5.000 lượt):
  │     ├── Nếu >= 3%: Kích hoạt triển khai Vector Cache 5 cổng (pgvector / Milvus / in-memory HNSW)
  │     ├── Nếu 1% - 3%: Chạy thử nghiệm SHADOW MODE trên 10% lưu lượng trong 7 ngày
  │     └── Nếu < 1%: Bắt buộc kiểm chứng qua Shadow Mode hoặc Embedding Cosine Similarity; ký duyệt nếu ngữ nghĩa cũng < 1%
  ├── [ ] Bổ sung điều kiện loại trừ get_top_trending_songs khỏi Semantic Cache ở Bước 8
  ├── [ ] Bổ sung bài test tích hợp DB thật (AiChatRepositoryIntegrationTest với Testcontainers MySQL) kiểm chứng Tie-Breaker collation
  └── [ ] Thiết lập Dashboard Prometheus/Grafana giám sát ai.cache.exact_repeat_hits, ai.cache.exact_repeat_24h_hits và kiểm thử tải
```

---

## 9. NHẬT KÝ THAY ĐỔI (CHANGELOG)

* **Hiệu chuẩn lần 17 (Hiện tại):**
  - **Đồng bộ Mục lục & Chuẩn hóa Anchor Links:** Khớp 100% tiêu đề các mục 3.1, 3.3, 3.4, 3.5, 4.3, 5.3, 7.2, 8; bổ sung mục 3.5 vào mục lục.
  - **Bảo chứng Thực nghiệm 58 ca FastPathEngine (109/109 Tests PASS):** Bổ sung 5 ca kiểm thử tham số hóa cho các chuỗi phủ định không dấu (`"dung tat nhac"`, `"ko dung lai"`, `"cho tat nhac"`, `"ko tat"`, `"dung tam dung"`) vào `FastPathEngineTest.java`. Nâng tổng số test của Fast-Path lên 58/58 PASS và toàn hệ thống lên 109/109 PASS (100% Pass Rate).
  - **Làm rõ Cơ chế SO_TIMEOUT (15s), Điều kiện 0-byte & `future.cancel(true)`:** Phân định rõ `readTimeout = 15s` là SO_TIMEOUT (idle read timeout). Khoảng thời gian socket tồn tại tối đa 7s (15s - 8s) áp dụng nghiêm ngặt cho điều kiện "không có byte nào được trả về" (0-byte starvation). Lệnh `future.cancel(true)` giải phóng luồng ứng dụng nhưng thread đọc I/O bên dưới của `HttpURLConnection` vẫn đợi đến khi socket timeout.
  - **Hiệu chuẩn Ngưỡng Watchdog 8000ms cho Gemini 2.5 Pro Thinking Budget:** Ghi nhận 8000ms là ngưỡng khởi điểm; mô hình Pro có internal thinking budget nên TTFT có thể vượt quá 8s trên câu hỏi nhạc lý phức tạp mà không phải lỗi. Cần giám sát phân phối p50/p95/p99 của `ai.latency.ttft` để tinh chỉnh ngưỡng trước khi vận hành thực tế.
  - **Sửa Cú pháp PromQL Alert sang `increase()` & Bounded Cardinality:** Thay thế `rate(...[5m]) > 5` (req/sec) bằng `increase(...[5m]) > 5` (đếm số sự kiện tuyệt đối gia tăng trong 5 phút); lưu ý giới hạn miền giá trị của nhãn `reason`.
  - **Thay thế Redis SET NX cho Đa Instance:** Thay thế đề xuất Redis HyperLogLog bằng Redis `SET key userId EX 86400 NX` (hoặc Prometheus aggregation) để theo dõi lặp lại liên instance chính xác do HLL không hỗ trợ truy vấn tồn tại (membership).
  - **Chuẩn hóa Collation MySQL & Single Source of Truth:** Làm rõ hầu hết collation `_ci` chuẩn sắp xếp `'USER' > 'AI'`, duy trì kế hoạch test với Testcontainers MySQL; xác lập `KE_HOACH_LOP_7_CACHE_ROUTER.md` là Single Source of Truth duy nhất.
* **Hiệu chuẩn lần 16:**
  - **Sửa lỗi cú pháp & Chuẩn hóa Persona:** Xóa bỏ dấu `};` thừa trong `resolveIdentityResponse`. Thống nhất tên enum `AiPersona.HEALING_FRIEND` trên toàn hệ thống (thay thế nhãn `HEALING_BUDDY`).
  - **Chuẩn hóa Văn phong Persona:** Cập nhật câu chào Turn 1 và giới thiệu danh tính cho `MUSIC_CRITIC` sang ngôi xưng trang trọng *"Kính chào quý thính giả..."* và `HEALING_FRIEND` sang *"Chào bạn, mình luôn ở đây để lắng nghe..."*.
  - **Đồng bộ Two-Tier FastPathEngine:** Bổ sung `"bài tiếp theo"` / `"bai tiep theo"`, thống nhất câu trả lời RESUME *"Đã tiếp tục phát nhạc."*, thêm unaccented fallback.
  - **Tách tiện ích dùng chung `VietnameseTextNormalizer.stripDiacritics`:** Bỏ wrapper thừa trong `CanCacheEvaluator`, gọi trực tiếp tiện ích tĩnh.
  - **Làm rõ Tie-Breaker theo String `sender`:** Xác định rõ `ChatMessage` dùng `@Enumerated(EnumType.STRING)` trên cột `VARCHAR(16)`, sắp xếp theo thứ tự bảng chữ cái Lexicographical (`'USER' > 'AI'`).
  - **Phân định Socket Timeout (15s vs 90s) & Safety Blocks (HTTP 200 vs 400):** Chỉ định rõ Watchdog phải trỏ vào `geminiRestClient` (15s read timeout), tránh rủi ro treo socket 90s; thiết lập Prometheus Alert độc lập cho lỗi 400/403 và in-stream safety blocks (`finishReason=SAFETY`).
  - **Chống Stale Data ở Bước 8:** Bổ sung điều kiện loại trừ `!usedTool("get_top_trending_songs")` khỏi bộ điều kiện lưu Semantic Cache.
  - **Chuẩn hóa Bản chất Metric & Khung Quyết Định 3 Nhánh:** Làm rõ công thức mẫu số đo lường *"Tỷ lệ prompt là bản lặp (Repeat Ratio)"* trên Dashboard; bổ sung ràng buộc bắt buộc kiểm chứng qua Shadow Mode hoặc Semantic Similarity trước khi ký duyệt Nhánh 3 (`< 1%`).
  - **Chuẩn hóa nhãn tiến độ:** Chuyển sang **`75% Code-Complete, Telemetry Instrumented`** phản ánh đúng hiện trạng kỹ thuật.
  - **Kiểm thử tự động:** Mở rộng và nghiệm thu **104/104 Unit & Integration Tests Passed (100% Pass Rate)**.
* **Hiệu chuẩn lần 15:**
  - Đồng bộ FastPathEngine Two-tier accented/unaccented, loại trừ HTTP 400 Safety block khỏi Breaker, cờ `(?U)` trong CanCacheEvaluator xử lý bẫy "nhạc Anh". Nghiệm thu 91/91 tests.
* **Hiệu chuẩn lần 14:**
  - Nâng cấp Flyway `V5` MySQL `DATETIME(6)` microsecond precision, bảo toàn `@PrePersist` trong AbstractAuditEntity, gom lưu `@Transactional saveFastPathMessages`.
* **Hiệu chuẩn lần 13:**
  - Hoàn thiện Giai đoạn 2: ModelRouterService, CircuitBreakerLifecycle, First-Token Watchdog 8000ms.

---

> [!TIP]
> **Tóm tắt trạng thái:** Lớp 7 đã hoàn thành **75% Code-Complete, Telemetry Instrumented** với **109/109 Unit & Integration tests passed (100% Pass Rate)**. Bản thiết kế chi tiết đã giải quyết trọn vẹn toàn bộ các góc khuất thực thi: từ bẫy JPA Auditing, độ chính xác `DATETIME(6)`, tính đối ngẫu tie-breaker theo bảng chữ cái string, cấu trúc ngoại lệ Resilience4j, cách ly Watchdog trên Virtual Threads, phân định socket timeout 15s vs 90s, đến hạ tầng Telemetry 2 cửa sổ đo lường ngầm. Khung Quyết Định Dựa Trên Dữ Liệu 3 nhánh kèm bước xác thực Semantic Similarity là chiếc chìa khóa đưa hệ thống cán đích 100% Production-Ready sau giai đoạn chạy thực tế.
