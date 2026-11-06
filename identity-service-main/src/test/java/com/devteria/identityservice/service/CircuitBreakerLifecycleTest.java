package com.devteria.identityservice.service;

import com.devteria.identityservice.configuration.AiRouterProperties;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.enums.ModelTier;
import com.devteria.identityservice.exception.FirstTokenTimeoutException;
import com.devteria.identityservice.exception.UpstreamAiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CircuitBreakerLifecycleTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private CircuitBreaker circuitBreaker;
    private AiRouterProperties properties;
    private SimpleMeterRegistry meterRegistry;
    private ModelRouterService modelRouterService;
    private ObjectMapper objectMapper;
    private GeminiToolDeclarationFactory toolDeclarationFactory;
    private MusicToolExecutor musicToolExecutor;
    private RestClient mockRestClient;
    private RestClient.RequestBodyUriSpec mockUriSpec;
    private RestClient.RequestBodySpec mockBodySpec;
    private RestClient.ResponseSpec mockResponseSpec;

    private static class StubMusicRetrievalService extends MusicRetrievalService {
        public StubMusicRetrievalService() {
            super(null, null, null, null);
        }

        @Override
        public List<SongCardDto> searchSongs(String query, String genre, String artist, int limit) {
            return List.of(SongCardDto.builder().id("song-1").name("Test Song").artistNames("Test Artist").build());
        }

        @Override
        public String formatSongsAsGroundingXml(List<SongCardDto> songs) {
            return "<retrieved_music_library count=\"1\"></retrieved_music_library>";
        }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        toolDeclarationFactory = new GeminiToolDeclarationFactory();
        musicToolExecutor = new MusicToolExecutor(new StubMusicRetrievalService());

        properties = new AiRouterProperties();
        properties.setFastPathEnabled(true);
        properties.setReasoningTierEnabled(true);
        properties.setFastTierModel("gemini-3.5-flash-lite");
        properties.setReasoningTierModel("gemini-2.5-pro");
        properties.setFirstTokenTimeoutMs(500); // 500ms for fast test execution

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .recordExceptions(
                        UpstreamAiException.class,
                        RestClientResponseException.class,
                        ResourceAccessException.class
                )
                .ignoreExceptions(ClientAbortException.class)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(Map.of(ModelRouterService.CIRCUIT_BREAKER_INSTANCE, config));
        circuitBreaker = circuitBreakerRegistry.circuitBreaker(ModelRouterService.CIRCUIT_BREAKER_INSTANCE);

        meterRegistry = new SimpleMeterRegistry();
        modelRouterService = new ModelRouterService(properties, circuitBreakerRegistry, meterRegistry);

        mockRestClient = mock(RestClient.class);
        mockUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        mockBodySpec = mock(RestClient.RequestBodySpec.class);
        mockResponseSpec = mock(RestClient.ResponseSpec.class);

        when(mockRestClient.post()).thenReturn(mockUriSpec);
        when(mockUriSpec.uri(anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.header(anyString(), anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.contentType(any())).thenReturn(mockBodySpec);
        when(mockBodySpec.body(anyString())).thenReturn(mockBodySpec);
        when(mockBodySpec.retrieve()).thenReturn(mockResponseSpec);
    }

    private AiAgentOrchestrator createOrchestrator(ExecutorService executor) {
        AiAgentOrchestrator orchestrator = new AiAgentOrchestrator(
                mockRestClient,
                mockRestClient,
                objectMapper,
                toolDeclarationFactory,
                musicToolExecutor,
                executor,
                new Semaphore(10),
                modelRouterService,
                properties,
                circuitBreakerRegistry,
                meterRegistry
        );
        ReflectionTestUtils.setField(orchestrator, "apiKey", "test-api-key");
        return orchestrator;
    }

    private String createTextResponseJson(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"" + text + "\"}]}}],\"usageMetadata\":{\"totalTokenCount\":50}}";
    }

    private String createToolCallJson(String toolName, String query) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"" + toolName + "\",\"args\":{\"query\":\"" + query + "\"}}}]}}]}";
    }

    private String createPlayerActionToolJson(String action, String songId) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"trigger_player_action\",\"args\":{\"action\":\"" + action + "\",\"song_id\":\"" + songId + "\"}}}]}}]}";
    }

    @Test
    @DisplayName("Kịch bản 1: ClientAbortException giải phóng permit, không ghi nhận lỗi vào Breaker")
    void testClientAbort_ReleasesPermit_DoesNotRecordErrorOnBreaker() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        when(mockResponseSpec.body(String.class)).thenAnswer(inv -> {
            throw new ClientAbortException("Socket client closed by user");
        });

        String criticPrompt = "Xin hãy phân tích hòa âm và phối khí chi tiết của ca khúc này giúp tôi";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(resultRef.get());
        assertEquals(MessageStatus.FAILED, resultRef.get().status());

        // Breaker KHÔNG được ghi nhận lỗi nào
        assertEquals(0, circuitBreaker.getMetrics().getNumberOfFailedCalls());
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());

        directExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 2: Lỗi upstream (500) khi chưa chạy tool -> Ghi nhận lỗi và Fallback sang Flash thành công")
    void testUpstreamError_ExecutedToolsCountZero_RecordsErrorAndFallsBackToFlash() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        // Lần gọi đầu (gemini-2.5-pro): Lỗi 500
        // Lần gọi thứ hai (fallback sang gemini-3.5-flash-lite): Thành công
        when(mockResponseSpec.body(String.class))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Google Gemini Overloaded"))
                .thenReturn(createTextResponseJson("Câu trả lời từ Flash-Lite sau fallback"));

        String criticPrompt = "Xin hãy phân tích hòa âm và cấu trúc giai điệu bài hát này thật chi tiết nhé";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        AiAgentOrchestrator.AgentTurnResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(MessageStatus.COMPLETE, result.status());
        assertTrue(result.wasFallbackTriggered(), "wasFallbackTriggered phải là true");
        assertEquals("gemini-3.5-flash-lite", result.actualModelUsed());
        assertFalse(result.degradedByBreaker());

        // Breaker ghi nhận 1 failed call cho lần gọi pro ban đầu
        assertEquals(1, circuitBreaker.getMetrics().getNumberOfFailedCalls());

        directExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 3A: Tool có tác dụng phụ (trigger_player_action) đã chạy -> CẤM Fallback, dừng với AI_SERVER_BUSY")
    void testUpstreamError_SideEffectToolExecuted_BlocksFallbackAndReturnsBusy() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        // Iteration 1: Gọi Tool phát nhạc có tác dụng phụ (hasSideEffectToolExecuted = true)
        // Iteration 2: Upstream trả lỗi 500
        when(mockResponseSpec.body(String.class))
                .thenReturn(createPlayerActionToolJson("PLAY", "song-1"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini internal error"));

        String criticPrompt = "Xin hãy phân tích hòa âm và bật phát bài hát này lên cho tôi nghe thử";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        AiAgentOrchestrator.AgentTurnResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(MessageStatus.FAILED, result.status());
        assertEquals("AI_SERVER_BUSY", result.errorMessage(), "Cấm fallback khi tool side-effect đã chạy; phải trả về AI_SERVER_BUSY");
        assertFalse(result.wasFallbackTriggered());

        directExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 3B: Tool chỉ đọc (search_music_library) đã chạy -> CHO PHÉP Fallback sang Flash an toàn")
    void testUpstreamError_ReadOnlyToolExecuted_AllowsFallbackToFlash() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        // Iteration 1: Gọi Tool tìm kiếm chỉ đọc (hasSideEffectToolExecuted = false)
        // Iteration 2 (Pro): Lỗi 500
        // Iteration 2 (Flash Fallback): Thành công sinh câu trả lời
        when(mockResponseSpec.body(String.class))
                .thenReturn(createToolCallJson(GeminiToolDeclarationFactory.TOOL_SEARCH_MUSIC_LIBRARY, "nhạc Trịnh"))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "Gemini internal error"))
                .thenReturn(createTextResponseJson("Đã phân tích phong cách nhạc Trịnh từ Flash"));

        String criticPrompt = "Xin hãy phân tích hòa âm và phong cách bài hát sau khi tìm kiếm";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        AiAgentOrchestrator.AgentTurnResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(MessageStatus.COMPLETE, result.status());
        assertTrue(result.wasFallbackTriggered(), "Cho phép fallback sang Flash vì tool tìm kiếm chỉ đọc");
        assertEquals("gemini-3.5-flash-lite", result.actualModelUsed());

        directExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 4: resultRecorded chống double release khi khối finally thực thi")
    void testFinallyBlock_PreventsDoubleRelease_WhenAlreadyRecorded() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        when(mockResponseSpec.body(String.class)).thenReturn(createTextResponseJson("Thành công mỹ mãn"));

        String criticPrompt = "Xin hãy phân tích hòa âm và cấu trúc tác phẩm này thật kỹ lưỡng";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertEquals(MessageStatus.COMPLETE, resultRef.get().status());
        assertEquals(1, circuitBreaker.getMetrics().getNumberOfSuccessfulCalls());

        directExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 5: Watchdog Timeout (> 500ms) kích hoạt FirstTokenTimeoutException và Fallback")
    void testWatchdogTimeout_ThrowsFirstTokenTimeoutException_AndHandlesFallback() throws Exception {
        ExecutorService asyncExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(asyncExecutor);

        // Lần 1: Timeout quá 500ms (ngủ 800ms)
        // Lần 2 (fallback Flash): Trả về ngay lập tức
        when(mockResponseSpec.body(String.class))
                .thenAnswer(inv -> {
                    Thread.sleep(800);
                    return createTextResponseJson("Quá muộn");
                })
                .thenReturn(createTextResponseJson("Phản hồi nhanh từ Flash"));

        String criticPrompt = "Xin hãy phân tích hòa âm và phối khí của bản thu âm này giúp tôi";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        AiAgentOrchestrator.AgentTurnResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(MessageStatus.COMPLETE, result.status());
        assertTrue(result.wasFallbackTriggered());
        assertEquals("gemini-3.5-flash-lite", result.actualModelUsed());

        // Breaker ghi nhận 1 failed call do FirstTokenTimeoutException (kế thừa UpstreamAiException)
        assertEquals(1, circuitBreaker.getMetrics().getNumberOfFailedCalls());

        asyncExecutor.shutdown();
    }

    @Test
    @DisplayName("Kịch bản 6: Breaker OPEN -> Hạ cấp trực tiếp sang FAST_TIER, đánh dấu degradedByBreaker = true")
    void testPermitAcquisition_DegradesDirectlyWhenBreakerOpen() throws Exception {
        ExecutorService directExecutor = Executors.newCachedThreadPool();
        AiAgentOrchestrator orchestrator = createOrchestrator(directExecutor);

        circuitBreaker.transitionToOpenState();

        when(mockResponseSpec.body(String.class)).thenReturn(createTextResponseJson("Phản hồi từ Flash khi circuit open"));

        String criticPrompt = "Xin hãy phân tích hòa âm và phối khí bản nhạc này giúp tôi";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AiAgentOrchestrator.AgentTurnResult> resultRef = new AtomicReference<>();

        SseEmitter emitter = new SseEmitter(10_000L);
        orchestrator.orchestrateTurn(
                "testuser",
                criticPrompt,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", criticPrompt)))),
                "System prompt",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                emitter,
                turnResult -> {
                    resultRef.set(turnResult);
                    latch.countDown();
                }
        );

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        AiAgentOrchestrator.AgentTurnResult result = resultRef.get();
        assertNotNull(result);
        assertEquals(MessageStatus.COMPLETE, result.status());
        assertTrue(result.degradedByBreaker(), "Phải đánh dấu degradedByBreaker = true");
        assertEquals("gemini-3.5-flash-lite", result.actualModelUsed());

        directExecutor.shutdown();
    }
}
