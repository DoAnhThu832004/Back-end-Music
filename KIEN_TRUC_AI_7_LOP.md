# 📘 CẨM NANG KIẾN TRÚC HỆ THỐNG AI 7 LỚP — TỪ Ý TƯỞNG ĐẾN PRODUCTION
> **Dành cho kỹ sư phần mềm & AI Engineer**  
> *Được biên soạn dựa trên phân tích chuyên sâu mã nguồn thực tế dự án Spring Boot 3 (Java 21), Google Gemini API & Android Client Kotlin.*

---

## 🗺️ TỔNG QUAN KIẾN TRÚC: HÀNH TRÌNH TỪ "CHATBOT" ĐẾN "AI AGENT"

Một ứng dụng AI thực tế không chỉ đơn giản là gọi API của OpenAI hay Google. Để hoạt động an toàn, chính xác và không bị "phá sản" vì tiền gọi API, hệ thống phải được bọc trong một **kiến trúc phòng thủ đa tầng (Defense-in-Depth)** gồm 7 lớp:

```mermaid
flowchart TD
    User(["👤 Người dùng cuối"]) -->|1. Gửi tin nhắn| Auth["🔐 Lớp 5: Phân quyền & RBAC<br/>(Spring Security / JWT / Chống IDOR)"]
    
    Auth --> FastPath{"⚡ Lớp 7: Fast-Path Engine<br/>(Khớp Regex Chào hỏi & Player Transport?)"}
    
    FastPath -- "Khớp (< 10ms)" --> FastOut["🚀 Phản hồi tức thì SSE<br/>(Bỏ qua Session Lock & LLM)"]
    FastOut --> User
    
    FastPath -- "Không khớp" --> GuardIn["🛡️ Lớp 6: Input Guardrail<br/>(Concurrency Lock / Rate Limit / Anti-Injection)"]
    
    GuardIn --> CacheEval["📊 Lớp 7: Can-Cache Evaluator & Telemetry<br/>(8 Cổng Bypass / Caffeine 50k 30m / Đo lặp lại)"]
    
    CacheEval --> Router["🔀 Lớp 7: Dynamic Model Router<br/>(Fast Tier vs Reasoning Tier + Breaker)"]
    
    Router --> Orch["🧠 Lớp 4: Agent Orchestrator<br/>(Multi-Step Agent Loop + Watchdog 8000ms)"]
    
    subgraph ContextAssembly[" Đóng gói ngữ cảnh "]
        Prompt["📜 Lớp 2: System Prompt & Persona<br/>(Quy tắc ứng xử / Giọng điệu)"]
        RAG["📚 Lớp 3: RAG & Hybrid Search<br/>(Vector Embedding + Lọc tiếng Việt)"]
    end
    
    Orch --> ContextAssembly
    ContextAssembly -->|Payload hoàn chỉnh| L1["⚡ Lớp 1: Model + API Client<br/>(Gemini REST API / Streaming SSE)"]
    
    L1 -->|Quyết định gọi Tool| ToolExec["⚙️ Lớp 4: Tool Executor<br/>(Chạy code Java thật / Database)"]
    ToolExec -->|Kết quả Tool| Orch
    
    L1 -->|Văn bản sinh ra| GuardOut["🛡️ Lớp 6: Output Guardrail<br/>(Kiểm duyệt nội dung / Chống lộ PII)"]
    
    GuardOut -->|Stream SSE từng chữ| User
```

---

## 📑 MỤC LỤC CHI TIẾT
1. [Lớp 1: Model + API (Nền móng & Kết nối LLM)](#1-lớp-1-model--api)
2. [Lớp 2: System Prompt (Định hình Vai trò, Giọng điệu & Giới hạn)](#2-lớp-2-system-prompt)
3. [Lớp 3: RAG (Truy xuất Dữ liệu Nội bộ & Chống Ảo giác)](#3-lớp-3-rag---retrieval-augmented-generation)
4. [Lớp 4: Công cụ & Agent Loop (Biến Chatbot thành AI Agent)](#4-lớp-4-công-cụ--multi-step-agent-loop)
5. [Lớp 5: Phân quyền & RBAC (Bảo mật Tiền định)](#5-lớp-5-phân-quyền--kiểm-soát-truy-cập-rbac)
6. [Lớp 6: Rào chắn - Guardrails (An ninh Hai chiều & Hạn mức)](#6-lớp-6-rào-chắn-guardrails--content-moderation)
7. [Lớp 7: Cache & Model Router (Tối Ưu Độ Trễ, Chi Phí & Định Tuyến)](#7-lớp-7-cache--model-router)
8. [Bảng đối chiếu tổng kết 7 lớp](#8-bảng-đối-chiếu-tổng-kết-7-lớp)
9. [Bước tiếp theo để đạt 100% Production-Ready](#9-bước-tiếp-theo-để-đạt-100-production-ready)

---

## 1. LỚP 1: MODEL + API

### 1.1. Bản chất & Nguyên lý
* **Không sáng chế lại bánh xe:** Việc huấn luyện mô hình nền tảng (Foundation Model) tốn hàng chục triệu USD. Doanh nghiệp chỉ cần tiêu thụ qua REST API hoặc SDK.
* **Cơ chế:** Gửi HTTP POST chứa Prompt/Ngữ cảnh lên máy chủ của nhà cung cấp (Google, OpenAI), nhận về JSON kết quả hoặc luồng Stream.
* **Nguy cơ nếu chỉ có Lớp 1:**
  1. *Độ trễ cao:* Chờ 5 - 15 giây mới nhận được toàn bộ câu trả lời nếu không dùng Streaming.
  2. *Cháy tài nguyên:* Không giới hạn số kết nối đồng thời làm nghẽn máy chủ.
  3. *Không có cấu trúc:* AI trả về văn bản tự do, dễ gây lỗi parse JSON ở Backend.

### 1.2. Mã nguồn triển khai thực tế

#### A. Cấu hình Timeout chuyên biệt cho Streaming và Non-Streaming
Trong `GeminiConfig.java`:
```java
@Configuration
public class GeminiConfig {

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    // 1. Client cho các tác vụ ngắn (Embedding, Moderation) - Timeout ngắn 15s
    @Bean(name = "geminiRestClient")
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    // 2. Client cho Streaming SSE chat dài - Nới lỏng Timeout lên 90s
    @Bean(name = "geminiStreamRestClient")
    public RestClient geminiStreamRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(90000); // 90 giây

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
```

#### B. Streaming SSE và Virtual Threads Java 21
Trong `AiAgentOrchestrator.java`, sử dụng Virtual Threads để xử lý luồng stream SSE với dung lượng đồng thời cao:
```java
@Bean(name = "aiStreamingExecutor")
public ExecutorService aiStreamingExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

---

## 2. LỚP 2: SYSTEM PROMPT

### 2.1. Bản chất & Nguyên lý
* Định hình tính cách (Persona), ranh giới nghiệp vụ, phong cách xưng hô và các quy tắc cấm kỵ (Guardrail rules).
* Cấu trúc thẻ XML/Markdown giúp LLM tách biệt rõ ràng giữa hướng dẫn hệ thống, ngữ cảnh dữ liệu và tin nhắn người dùng.

### 2.2. Mã nguồn triển khai thực tế
Trong `AiPersonaPromptFactory.java`, hệ thống hỗ trợ 3 Persona âm nhạc chuyên biệt:
1. `GENZ_DJ`: Năng động, dùng từ lóng Gen Z (*"cháy"*, *"nghiện điên"*, *"bật nóc"*).
2. `MUSIC_CRITIC`: Phân tích chuyên sâu về nhạc lý, cấu trúc hòa âm, phối khí.
3. `HEALING_FRIEND`: Nhẹ nhàng, đồng cảm, gợi ý các bài hát êm dịu xoa dịu tâm hồn (thay thế tên nháp cũ `HEALING_BUDDY` để khớp 100% với enum `com.devteria.identityservice.enums.AiPersona`).

---

## 3. LỚP 3: RAG (RETRIEVAL-AUGMENTED GENERATION)

### 3.1. Bản chất & Nguyên lý
* LLM không biết dữ liệu nội bộ của ứng dụng. RAG giúp truy xuất thông tin bài hát thực tế từ Database thông qua Vector Embedding kết hợp Tìm kiếm Từ khóa (Hybrid Search).
* Ngăn chặn hoàn toàn hiện tượng Ảo giác (Hallucination) — LLM tự bịa ra bài hát hoặc ca sĩ không tồn tại.

### 3.2. Mã nguồn triển khai thực tế
* `SongEmbeddingService.java`: Gọi Gemini Embedding Model (`gemini-embedding-001`) để sinh vector 768 chiều cho bài hát và câu hỏi.
* `MusicRetrievalService.java`: Tính Cosine Similarity giữa vector câu hỏi và vector bài hát đã lưu trong bộ nhớ / Database, kết hợp lọc theo thể loại và ca sĩ.

---

## 4. LỚP 4: CÔNG CỤ & MULTI-STEP AGENT LOOP

### 4.1. Bản chất & Nguyên lý
* Biến Chatbot từ "chỉ biết nói" thành Agent "biết hành động".
* Cung cấp Function Calling cho LLM:
  1. `trigger_player_action`: Tạm dừng, tiếp tục, phát bài hát cụ thể.
  2. `propose_ai_playlist`: Đề xuất tạo danh sách phát tự động gồm các bài hát hợp lệ.
  3. `search_music_library`: Tìm kiếm tra cứu catalog bài hát.

### 4.2. Mã nguồn triển khai thực tế
* `GeminiToolDeclarationFactory.java`: Khai báo schema JSON cho các tool để Gemini hiểu tham số.
* `MusicToolExecutor.java`: Thực thi các lệnh Java thật, kèm cơ chế Anti-Hallucination Allowlist (loại bỏ bài hát không có trong thư viện).
* `AiAgentOrchestrator.java`: Quản lý vòng lặp đàm thoại tối đa 3 vòng lặp (`MAX_TOOL_ITERATIONS = 3`).

---

## 5. LỚP 5: PHÂN QUYỀN & KIỂM SOÁT TRUY CẬP (RBAC)

### 5.1. Bản chất & Nguyên lý
* Chặn đứng lỗ hổng IDOR (Insecure Direct Object References): Người dùng A không thể đọc trộm hay gửi tin nhắn vào phiên chat của người dùng B.
* Xác thực JWT token, lấy thông tin người dùng từ SecurityContext và kiểm tra quyền sở hữu `ChatSession`.

### 5.2. Mã nguồn triển khai thực tế
Trong `AiChatController.java`:
```java
ChatSession session = chatSessionRepository.findById(sessionId)
        .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_EXISTED));

if (!session.getUser().getId().equals(currentUser.getId())) {
    log.warn("[AI Chat] Người dùng {} cố tình truy cập trái phép session {} (IDOR)", currentUser.getUsername(), sessionId);
    throw new AppException(ErrorCode.UNAUTHORIZED);
}
```

---

## 6. LỚP 6: RÀO CHẮN - GUARDRAILS & CONTENT MODERATION

### 6.1. Bản chất & Nguyên lý
* **Bảo vệ hai chiều:**
  * *Chiều vào (Input Guardrail):* Khóa đồng thời (Concurrency Lock) chống spam đa luồng trên cùng 1 session, Rate Limiting (10 req/phút), chống Prompt Injection.
  * *Chiều ra (Output Guardrail):* Kiểm duyệt ngôn từ độc hại (Hate Speech, Toxic, Cờ bạc), che giấu thông tin cá nhân PII.

### 6.2. Mã nguồn triển khai thực tế
* `AiChatGuardService.java`: Quản lý Semaphore 50 luồng toàn hệ thống và ConcurrentHashMap session lock.
* `AiModerationService.java`: Kiểm duyệt nội dung am hiểu tiếng lóng Gen Z (phân biệt giữa khen *"cháy khét lẹt"*, *"hay vcl"* là SAFE, và lăng mạ xúc phạm là AUTO_HIDE).

---

## 7. LỚP 7: CACHE & MODEL ROUTER

Lớp 7 là đỉnh cao của kiến trúc tối ưu hóa hiệu năng, giảm thiểu tối đa chi phí gọi API và độ trễ phản hồi:

### 7.1. Fast-Path Command Engine (< 10ms)
* **Vị trí trước Session Lock:** Chạy trước `checkAndLockSession` để người dùng có thể gửi lệnh *"tạm dừng"* ngay lập tức mà không bị chặn khi bot đang stream câu trả lời dài.
* **Regex 2 Tầng Siêu Tốc & Bổ sung "Bài Tiếp Theo":** Quét có dấu (NFC) và không dấu (`VietnameseTextNormalizer.stripDiacritics`), xử lý chào hỏi Turn 1, giới thiệu danh tính và lệnh Player Transport (`PAUSE`, `RESUME`, `NEXT` với phản hồi đồng bộ *"Đã gửi lệnh chuyển bài."*). Hỗ trợ đầy đủ biến thể *"bài tiếp theo"* / *"bai tiep theo"*.
* **Độ chính xác Microsecond & Dual Tie-Breaker (String Lexicographical & MySQL Collation):**
  * Migration Flyway `V5` nâng cấp `created_at` / `updated_at` lên `DATETIME(6)`.
  * `@Transactional saveFastPathMessages` gom lưu `userMessage` và `aiMessage` (+5ms).
  * `ChatMessage` lưu `sender` dạng `@Enumerated(EnumType.STRING)` trên cột `VARCHAR(16)`. Trong mã ASCII & mọi Collation MySQL chuẩn: `'U'` (85) > `'A'` (65) nên `'USER' > 'AI'`.
  * Sắp xếp đối ngẫu chuẩn xác: `ORDER BY m.createdAt ASC, m.sender DESC` (truy vấn xuôi, USER trước AI) và `ORDER BY m.createdAt DESC, m.sender ASC` (Sliding Window ngược, AI trước USER, sau khi `Collections.reverse()` thì USER luôn đứng trước AI).
* **Bảo Chứng Thực Nghiệm Regex 2 Tầng (58 Tests) & Phân Tích Nhập Nhằng Ngữ Nghĩa:**
  * Đã nghiệm thu **58/58 tests PASS** trong `FastPathEngineTest.java` bao phủ toàn diện 2 tầng: tầng có dấu (`"đừng tắt nhạc"`, `"không dừng lại"`, `"chớ tắt nhạc"`, `"đừng tạm dừng"`) chặn triệt để tại Bước 2; tầng không dấu (`"dung tat nhac"`, `"ko dung lai"`, `"cho tat nhac"`, `"ko tat"`, `"dung tam dung"`) chặn qua neo đầu `^` và cuối `$`.
  * Cảnh báo bẫy thiết kế: Tuyệt đối không phỏng đoán chặn tiền tố `"dung "` bừa bãi vì trong tiếng Việt không dấu, `"dung"` vừa là "dừng" vừa là "đừng". Các cụm từ nhập nhằng như `"dung phat"` (đừng phát vs dừng phát) hay `"dung tiep"` (dừng tiếp vs đừng tiếp) phải được phân tích dựa trên dữ liệu log truy vấn người dùng thực tế trước khi thêm quy tắc; các gợi ý regex như `^dung (?:tat|chuyen)\b` tuyệt đối loại trừ `phat`, `tiep`.

### 7.2. Dynamic Model Router & Resilience4j Circuit Breaker
* **Định tuyến Mô hình Thông minh:**
  * Làn A (Fast Tier): Mặc định sử dụng `gemini-3.5-flash-lite` cho các câu hỏi thông thường.
  * Làn B (Reasoning Tier): Điều hướng sang `gemini-2.5-pro` khi persona là `MUSIC_CRITIC`, độ dài $> 40$ ký tự và chứa từ khóa nhạc lý không dấu (*"hoa am"*, *"phoi khi"*, *"cau truc giai dieu"*...).
* **Ngắt Mạch Resilience4j & First-Token Watchdog 8000ms:**
  * Cấu hình instance `geminiCriticRouter` (sliding window 120s, failure rate 50%, wait in open 30s).
  * Phân cấp ngoại lệ `UpstreamAiException` để Circuit Breaker ghi nhận chính xác mọi lỗi I/O của Gemini; loại trừ 400 (Safety block) và 403 khỏi `record-exceptions` chống ngắt mạch oan.
  * **Phân định Socket Timeout (15s vs 90s) & Cơ chế `future.cancel(true)`:** First-token Watchdog 8000ms thực thi qua `CompletableFuture.supplyAsync(..., aiStreamingExecutor)` trên Virtual Threads và **bắt buộc gọi qua `geminiRestClient` (15s read timeout)**. `readTimeout = 15s` là SO_TIMEOUT (idle read timeout); lệnh `future.cancel(true)` giải phóng luồng gọi của ứng dụng nhưng thread đọc I/O bên dưới của `HttpURLConnection` vẫn chờ đến khi socket timeout (tối đa thêm 7s trong điều kiện **0-byte starvation**, tức Google không trả về byte nào). Tuyệt đối không dùng `geminiStreamRestClient` (90s).
  * **Độ trễ Gemini 2.5 Pro Thinking Budget & Hiệu chỉnh Ngưỡng:** 8000ms là ngưỡng khởi điểm; mô hình Pro có internal thinking budget nên TTFT câu hỏi phức tạp có thể vượt quá 8s. Cần giám sát p50/p95/p99 của `ai.latency.ttft` để tinh chỉnh ngưỡng trước khi sản xuất.
  * **Đề xuất Two-Phase Client:** Khi hỗ trợ full-streaming cho Pro, tách thành 2 giai đoạn: Giai đoạn 1 giám sát first-token bằng client ngắn, Giai đoạn 2 stream dữ liệu bằng client dài (90s) chống ngắt giữa chừng.
  * **Giám sát Safety Blocks & Alert Độc Lập qua PromQL `increase()`:** Xử lý in-stream safety block HTTP 200 (`finishReason=SAFETY`, `promptFeedback.blockReason`) như HTTP OK đối với Breaker. Khai báo counter `ai.gemini.client.errors` (400/403) và `ai.gemini.safety.blocks` (với 2 nhãn cố định: `type` gồm `"prompt"` vs `"response"`, và `category` giới hạn qua enum đã nghiệm thu `SafetyBlockCategory` gồm 5 nhóm: `SAFETY`, `RECITATION`, `POLICY`, `PRIVACY`, `OTHER`; đặc tả Gemini API v1beta & gemini-3.5 (cần xác nhận khi chạy thực tế); cả hai hàm ánh xạ prompt & candidate đều trả `Optional` an toàn, loại trừ `STOP`, `MAX_TOKENS`, `OTHER`, `LANGUAGE` khỏi counter). Cảnh báo Prometheus Alert độc lập: `(sum(increase(ai_gemini_client_errors_total{status=~"400|403"}[5m])) or on() vector(0)) + (sum(increase(ai_gemini_safety_blocks_total[5m])) or on() vector(0)) > 5`. Cấu trúc `or on() vector(0)` triệt tiêu lỗi vector rỗng khi một vế vắng mặt khi đăng ký theo sự kiện đầu tiên (hoặc khuyến nghị đăng ký Eager lúc khởi động); ngưỡng alert được hiệu chỉnh theo baseline lưu lượng thực tế.
* **Tinh chỉnh Fallback Guard (Read-only vs Side-effect):**
  * Cho phép fallback an toàn về Flash khi chỉ gọi các tool đọc (`search_music_library`, `get_top_trending_songs`, `get_user_recommendations`).
  * Chặn đứng fallback khi đã gọi tool có tác dụng phụ (`trigger_player_action`, `propose_ai_playlist`) để ngăn lỗi Double Mutation.

### 7.3. CanCacheEvaluator & Hạ Tầng Telemetry Ngầm
* **8 Cổng Kiểm Định Cache & Giảm Thiểu False-Positive:** Quét 2 tầng Có dấu (cờ `(?U)`) và Không dấu phân loại 8 lý do BYPASS: `EMPTY_PROMPT`, `HISTORY`, `HAS_SONG_CONTEXT`, `PERSONAL`, `PII`, `ANAPHORA`, `TEMPORAL`, `DANGLING_REF`. Áp dụng pattern `nhạc của (?:tôi|mình|em|anh)` giúp giảm thiểu false-positive (chấp nhận over-bypass có chủ ý cho *"nhạc của anh"* để bảo vệ quyền riêng tư).
* **Gọi trực tiếp tiện ích:** Sử dụng trực tiếp `VietnameseTextNormalizer.stripDiacritics(lowerAccented)` thay vì bọc wrapper method.
* **Loại trừ dữ liệu biến động nhanh ở Bước 8:** Bổ sung điều kiện `!usedTool("get_top_trending_songs")` vào bộ tiêu chuẩn lưu cache để chống hiện tượng cache kết quả bảng xếp hạng nhanh lỗi thời (Stale Data).
* **TelemetryService (Caffeine 50k, 30m & 24h):**
  * Chuẩn hóa Canonical Prompt $\rightarrow$ SHA-256 tích hợp Persona (`persona:canonical`), triệt để tuân thủ nguyên tắc Persona Isolation (Cổng 1 của Cache).
  * Phân biệt rõ rệt giữa lặp lại liên người dùng (`ai.cache.exact_repeat_hits` / `ai.cache.exact_repeat_24h_hits`) và cùng người dùng thử lại (`ai.cache.self_repeat_hits` / `ai.cache.self_repeat_24h_hits`).
  * Tách riêng người dùng ẩn danh bằng metric `ai.cache.anonymous_prompts` và không tính vào cross-user repeat cache để bảo vệ tính chính xác của tỷ lệ lặp lại tiềm năng.
  * Hoàn toàn fail-safe: Lỗi telemetry không bao giờ làm gián đoạn luồng đàm thoại.
  * **Minh bạch hóa giới hạn đo lường:** Ghi nhận sai lệch biên câu hỏi trống ($< 0.01\%$), giới hạn 50k W-TinyLFU và đặc thù in-memory single-instance càng củng cố tính chất "cận dưới thận trọng" (Strict Lower Bound) của tỷ lệ lặp. Khi triển khai cụm đa instance, sử dụng Prometheus aggregation hoặc Redis nguyên tử `SET telemetry:24h:{sha256} {userId} EX 86400 NX GET` (Redis 7+). Khi khóa tồn tại, Redis không ghi đè và không làm mới TTL, bảo đảm cửa sổ 24h tính cố định từ lần prompt đầu tiên (khớp hành vi `expireAfterWrite` của Caffeine); đồng thời trả về `oldUserId` phân biệt chính xác giữa self-repeat và cross-user repeat. Trên Redis < 7.0, sử dụng script Lua nguyên tử kiểm tra `GET` trước rồi mới `SET ... EX` (lưu ý `redis.call('GET')` trả về Lua `false` khi vắng khóa, `if old then` xử lý đúng và `return nil` trả về Null Bulk String reply chuẩn).

### 7.4. Khung Quyết Định Kiến Trúc Dựa Trên Dữ Liệu 3 Nhánh
* Thu thập dữ liệu thực tế với cỡ mẫu đủ lớn $N \ge 5.000$ lượt hợp lệ (hoặc tối thiểu 3.000 lượt trên Staging/Production trong 14 ngày) để bảo đảm lực thống kê (Statistical Power) cho ngưỡng 1% và 3%:
  * **Bản chất Metric:** Công thức tính phản ánh **Tỷ lệ Cache Hit tiềm năng (Potential Cache Hit Rate)** nếu hệ thống có cache; không phải là hit rate của một cache đang chạy thật (vì lượt đầu $k=1$ luôn là MISS).
  * **Nếu Tỷ lệ Lặp lại Độc lập $\ge 3\%$:** Triển khai Hệ thống Vector Cache 5 cổng + Event Invalidation.
  * **Nếu Tỷ lệ Lặp lại từ $1\% - 3\%$:** Kích hoạt chế độ **SHADOW MODE** trên 10% lưu lượng trong 7 ngày để đo lường độ trễ và mức tiết kiệm token thực tế trước khi cấp phát tài nguyên cho Vector DB.
  * **Nếu Tỷ lệ Lặp lại Độc lập $< 1\%$:** **Bắt buộc trải qua bước kiểm chứng ngữ nghĩa ngầm** (Shadow Mode 5–10% lưu lượng hoặc đo Cosine Similarity embedding trên tập mẫu $N \ge 1.000$ câu để xác nhận tỷ lệ lặp ngữ nghĩa cũng $< 1\%$). Khi đó mới chính thức **Ký duyệt (Sign-off) Nghiệm thu 100% Production-Ready mà KHÔNG cần Semantic Cache**. Tiết kiệm bộ nhớ RAM, CPU và giảm chi phí vận hành (Pragmatic Engineering).

---

## 8. BẢNG ĐỐI CHIẾU TỔNG KẾT 7 LỚP

| Lớp | Tên gọi | Nhiệm vụ cốt lõi | Vấn đề giải quyết | File đại diện trong Backend |
| :---: | :--- | :--- | :--- | :--- |
| **1** | **Model + API** | Kết nối LLM qua HTTP, quản lý Timeout & SSE Streaming | Loại bỏ độ trễ chờ đợi, hỗ trợ JSON có cấu trúc | `GeminiConfig`, `GeminiClientService` |
| **2** | **System Prompt** | Định hình tính cách (Persona), ranh giới nghiệp vụ & đạo đức | Chống AI nói nhảm, lạc đề, ngăn chặn tư vấn ngoài luồng | `AiPersonaPromptFactory` |
| **3** | **RAG** | Tìm kiếm dữ liệu nội bộ bằng Vector & Từ khóa tiếng Việt | Chống Ảo giác (Hallucination), giới thiệu đúng dữ liệu thật | `SongEmbeddingService`, `MusicRetrievalService` |
| **4** | **Tools & Agent** | Cấp quyền gọi API điều khiển hệ thống, chạy vòng lặp Agent | Biến bot "chỉ biết nói" thành Agent "biết hành động" | `GeminiToolDeclarationFactory`, `AiAgentOrchestrator` |
| **5** | **Phân quyền RBAC** | Xác thực JWT, lọc Tool theo Role, khóa cứng định danh | Ngăn chặn rò rỉ dữ liệu nhạy cảm, chặn đứng IDOR | `SecurityConfig`, `AiChatController` |
| **6** | **Guardrails** | Soi chiếu dữ liệu 2 chiều, chống Prompt Injection, khóa Spam | Bảo vệ an toàn ứng dụng, kiểm duyệt nội dung, chặn nổ ví API | `AiChatGuardService`, `AiModerationService` |
| **7** | **Cache & Router** | Fast-Path < 10ms, Model Router, Resilience4j, Telemetry | Tối ưu độ trễ, tiết kiệm token, tự phục hồi lỗi upstream | `FastPathEngine`, `ModelRouterService`, `CanCacheEvaluator`, `TelemetryService` |

---

## 9. LỘ TRÌNH THỰC THI 3 ƯU TIÊN VẬN HÀNH (HƯỚNG TỚI 100% PRODUCTION-READY)

Sau khi hoàn tất xuất sắc toàn bộ mã nguồn của 7 lớp và đóng băng chu kỳ hiệu chỉnh văn bản tài liệu (**125/125 Unit Tests PASS — 100% Pass Rate + 3 Testcontainers MySQL Integration Tests**; tham chiếu Single Source of Truth tại [`KE_HOACH_LOP_7_CACHE_ROUTER.md#muc-1-hien-trang`](KE_HOACH_LOP_7_CACHE_ROUTER.md#muc-1-hien-trang)):

1. **Thứ tự ưu tiên 1 (Local / CI — [✅ ĐÃ HOÀN TẤT 100%]):**
   * Hoàn tất điều kiện `canSaveToSemanticCache()` ở Bước 8 trong `AiAgentOrchestrator`, loại trừ hoàn toàn `get_top_trending_songs`, dữ liệu cá nhân hóa và các lượt hội thoại bị lỗi / hạ cấp.
   * Xây dựng `AiChatRepositoryIntegrationTest` với Testcontainers MySQL 8.0 (`@Testcontainers(disabledWithoutDocker = true)`) kiểm chứng thực nghiệm Dual Tie-Breaker Collation (`ASC, DESC` và `DESC, ASC`) cùng độ phân giải `DATETIME(6)`.
2. **Thứ tự ưu tiên 2 (Môi trường Staging — [⏳ ĐANG MỞ]):**
   * Dựng Grafana Dashboard theo dõi phân phối TTFT (`ai.latency.ttft`) của Reasoning Tier (`gemini-2.5-pro`) dưới áp lực Thinking Budget (prompt nhạc lý phức tạp) để chốt cứng cấu hình `first-token-timeout-ms` (thay thế ngưỡng dự phòng 8000ms).
   * Cấu hình Prometheus Alert rule (`AiGeminiSafetyOrClientErrorsSpike`) với PromQL `increase(...[5m]) + or on() vector(0)` cảnh báo đột biến lỗi 400/403.
3. **Thứ tự ưu tiên 3 (Sau khi Go-Live — [📈 THEO DÕI SẢN XUẤT]):**
   * Thu thập dữ liệu Telemetry 2 cửa sổ (30m & 24h) với mẫu $N \ge 5.000$ lượt prompt hợp lệ để áp dụng Khung Quyết Định 3 Nhánh ($<1\%$, $1-3\%$, $\ge 3\%$) cho Semantic Cache.
4. **Lớp 8 (Observability & CI/CD Evals dài hạn):**
   * *LLM Tracing:* Tích hợp OpenTelemetry / Micrometer Tracing theo dõi từng span của Multi-step Agent.
   * *Automated Evals:* Duy trì bộ kiểm thử tự động `AiCompanionEvaluationSuiteTest` trong pipeline CI/CD mỗi khi cập nhật Prompt!
