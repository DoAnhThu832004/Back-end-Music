package com.devteria.identityservice.service;

import com.devteria.identityservice.configuration.AiRouterProperties;
import com.devteria.identityservice.dto.response.PlayerActionDto;
import com.devteria.identityservice.dto.response.PlaylistProposalDto;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.enums.ModelTier;
import com.devteria.identityservice.enums.SafetyBlockCategory;
import com.devteria.identityservice.exception.FirstTokenTimeoutException;
import com.devteria.identityservice.exception.UpstreamAiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bộ điều phối Multi-Step Agent Loop cho Gemini Function Calling (Lớp 7 - Stage 2).
 * - Quản lý tối đa MAX_TOOL_ITERATIONS = 3 vòng lặp công cụ.
 * - Tích hợp ModelRouterService & Resilience4j Circuit Breaker (geminiCriticRouter).
 * - Permit Lifecycle & First-Token Watchdog 8000ms.
 * - Chặn tuyệt đối Fallback khi executedToolsCount > 0 (tránh lặp tool).
 * - Phân loại ngoại lệ theo nguồn: ClientAbortException vs Upstream errors.
 */
@Service
@Slf4j
public class AiAgentOrchestrator {

    public static final int MAX_TOOL_ITERATIONS = 3;

    private final RestClient geminiRestClient;
    private final RestClient geminiStreamRestClient;
    private final ObjectMapper objectMapper;
    private final GeminiToolDeclarationFactory toolDeclarationFactory;
    private final MusicToolExecutor musicToolExecutor;
    private final ExecutorService aiStreamingExecutor;
    private final Semaphore aiStreamingSemaphore;
    private final ModelRouterService modelRouterService;
    private final AiRouterProperties routerProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final CanCacheEvaluator canCacheEvaluator;
    private final TelemetryService telemetryService;

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.5-flash-lite}")
    private String model;

    public record AgentTurnResult(
            String fullText,
            int totalTokens,
            MessageStatus status,
            List<SongCardDto> suggestedSongs,
            PlayerActionDto playerAction,
            PlaylistProposalDto playlistProposal,
            String errorMessage,
            boolean usedPersonalTool,
            boolean usedTrendingSongsTool,
            boolean wasFallbackTriggered,
            boolean degradedByBreaker,
            String actualModelUsed
    ) {
        public AgentTurnResult(
                String fullText,
                int totalTokens,
                MessageStatus status,
                List<SongCardDto> suggestedSongs,
                PlayerActionDto playerAction,
                PlaylistProposalDto playlistProposal,
                String errorMessage
        ) {
            this(fullText, totalTokens, status, suggestedSongs, playerAction, playlistProposal, errorMessage, false, false, false, false, "gemini-3.5-flash-lite");
        }

        public AgentTurnResult(
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
        ) {
            this(fullText, totalTokens, status, suggestedSongs, playerAction, playlistProposal, errorMessage, usedPersonalTool, false, wasFallbackTriggered, degradedByBreaker, actualModelUsed);
        }

        public static AgentTurnResult forCacheHit(String text, List<SongCardDto> songs) {
            return new AgentTurnResult(
                    text, 0, MessageStatus.COMPLETE, songs != null ? songs : Collections.emptyList(),
                    null, null, null, false, false, false, false, "semantic-cache"
            );
        }

        public static AgentTurnResult failed(String errorCode) {
            return new AgentTurnResult("", 0, MessageStatus.FAILED, Collections.emptyList(), null, null, errorCode, false, false, false, false, "none");
        }

        public static AgentTurnResult interrupted(String fullText, int totalTokens, String errorMessage) {
            MessageStatus status = (fullText != null && !fullText.isBlank()) ? MessageStatus.INTERRUPTED : MessageStatus.FAILED;
            return new AgentTurnResult(fullText != null ? fullText : "", totalTokens, status, Collections.emptyList(), null, null, errorMessage, false, false, false, false, "unknown");
        }

        public static AgentTurnResult success(
                String fullText, int totalTokens, List<SongCardDto> songs,
                PlayerActionDto playerAction, PlaylistProposalDto proposal,
                String modelUsed, boolean usedPersonalTool, boolean usedTrendingSongsTool,
                boolean wasFallbackTriggered, boolean degradedByBreaker) {
            return new AgentTurnResult(
                    fullText, totalTokens, MessageStatus.COMPLETE, songs, playerAction, proposal, null,
                    usedPersonalTool, usedTrendingSongsTool, wasFallbackTriggered, degradedByBreaker, modelUsed
            );
        }

        public static AgentTurnResult success(
                String fullText, int totalTokens, List<SongCardDto> songs,
                PlayerActionDto playerAction, PlaylistProposalDto proposal,
                String modelUsed, boolean usedPersonalTool, boolean wasFallbackTriggered, boolean degradedByBreaker) {
            return success(fullText, totalTokens, songs, playerAction, proposal, modelUsed, usedPersonalTool, false, wasFallbackTriggered, degradedByBreaker);
        }

        /**
         * Tiêu chuẩn Bước 8: Kiểm định điều kiện an toàn để lưu vào Semantic Cache (W-TinyLFU)
         * - Chỉ lưu khi lượt hội thoại hoàn tất thành công (COMPLETE)
         * - KHÔNG chứa thông tin cá nhân hóa (!usedPersonalTool)
         * - KHÔNG chứa thông tin biến động nhanh (!usedTrendingSongsTool)
         * - KHÔNG bị kích hoạt fallback (!wasFallbackTriggered)
         * - KHÔNG bị ngắt mạch / hạ cấp (!degradedByBreaker)
         * - KHÔNG chứa side-effect actions (playerAction == null && playlistProposal == null)
         */
        public boolean canSaveToSemanticCache() {
            return status == MessageStatus.COMPLETE
                    && !usedPersonalTool
                    && !usedTrendingSongsTool
                    && !wasFallbackTriggered
                    && !degradedByBreaker
                    && playerAction == null
                    && playlistProposal == null;
        }
    }

    public AiAgentOrchestrator(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("geminiStreamRestClient") RestClient geminiStreamRestClient,
            ObjectMapper objectMapper,
            GeminiToolDeclarationFactory toolDeclarationFactory,
            MusicToolExecutor musicToolExecutor,
            @Qualifier("aiStreamingExecutor") ExecutorService aiStreamingExecutor,
            @Qualifier("aiStreamingSemaphore") Semaphore aiStreamingSemaphore) {
        this(
                geminiRestClient,
                geminiStreamRestClient,
                objectMapper,
                toolDeclarationFactory,
                musicToolExecutor,
                aiStreamingExecutor,
                aiStreamingSemaphore,
                new ModelRouterService(new AiRouterProperties(), CircuitBreakerRegistry.ofDefaults(), null),
                new AiRouterProperties(),
                CircuitBreakerRegistry.ofDefaults(),
                null
        );
    }

    public AiAgentOrchestrator(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("geminiStreamRestClient") RestClient geminiStreamRestClient,
            ObjectMapper objectMapper,
            GeminiToolDeclarationFactory toolDeclarationFactory,
            MusicToolExecutor musicToolExecutor,
            @Qualifier("aiStreamingExecutor") ExecutorService aiStreamingExecutor,
            @Qualifier("aiStreamingSemaphore") Semaphore aiStreamingSemaphore,
            ModelRouterService modelRouterService,
            AiRouterProperties routerProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this(
                geminiRestClient,
                geminiStreamRestClient,
                objectMapper,
                toolDeclarationFactory,
                musicToolExecutor,
                aiStreamingExecutor,
                aiStreamingSemaphore,
                modelRouterService,
                routerProperties,
                circuitBreakerRegistry,
                meterRegistry,
                new CanCacheEvaluator(),
                new TelemetryService(meterRegistry)
        );
    }

    @Autowired
    public AiAgentOrchestrator(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("geminiStreamRestClient") RestClient geminiStreamRestClient,
            ObjectMapper objectMapper,
            GeminiToolDeclarationFactory toolDeclarationFactory,
            MusicToolExecutor musicToolExecutor,
            @Qualifier("aiStreamingExecutor") ExecutorService aiStreamingExecutor,
            @Qualifier("aiStreamingSemaphore") Semaphore aiStreamingSemaphore,
            ModelRouterService modelRouterService,
            AiRouterProperties routerProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) MeterRegistry meterRegistry,
            @Autowired(required = false) CanCacheEvaluator canCacheEvaluator,
            @Autowired(required = false) TelemetryService telemetryService) {
        this.geminiRestClient = geminiRestClient;
        this.geminiStreamRestClient = geminiStreamRestClient != null ? geminiStreamRestClient : geminiRestClient;
        this.objectMapper = objectMapper;
        this.toolDeclarationFactory = toolDeclarationFactory;
        this.musicToolExecutor = musicToolExecutor;
        this.aiStreamingExecutor = aiStreamingExecutor != null ? aiStreamingExecutor : Executors.newVirtualThreadPerTaskExecutor();
        this.aiStreamingSemaphore = aiStreamingSemaphore != null ? aiStreamingSemaphore : new Semaphore(50);
        this.modelRouterService = modelRouterService;
        this.routerProperties = routerProperties != null ? routerProperties : new AiRouterProperties();
        this.circuitBreakerRegistry = circuitBreakerRegistry != null ? circuitBreakerRegistry : CircuitBreakerRegistry.ofDefaults();
        this.meterRegistry = meterRegistry;
        this.canCacheEvaluator = canCacheEvaluator != null ? canCacheEvaluator : new CanCacheEvaluator();
        this.telemetryService = telemetryService != null ? telemetryService : new TelemetryService(meterRegistry);
    }

    /**
     * Quá tải 6 tham số để tương thích ngược với các caller cũ và unit test cũ.
     */
    public void orchestrateTurn(
            String currentUsername,
            String userRawPrompt,
            List<Map<String, Object>> initialContents,
            String systemInstruction,
            SseEmitter emitter,
            Consumer<AgentTurnResult> onCompleteCallback) {
        orchestrateTurn(
                currentUsername,
                userRawPrompt,
                initialContents,
                systemInstruction,
                AiPersona.GENZ_DJ,
                null,
                false,
                emitter,
                onCompleteCallback
        );
    }

    /**
     * Điều phối toàn bộ một lượt tương tác Agentic Chat trên luồng SSE kèm Model Routing & Breaker Lifecycle.
     */
    public void orchestrateTurn(
            String currentUsername,
            String userRawPrompt,
            List<Map<String, Object>> initialContents,
            String systemInstruction,
            AiPersona currentPersona,
            String currentSongId,
            boolean hasPriorHistory,
            SseEmitter emitter,
            Consumer<AgentTurnResult> onCompleteCallback) {

        aiStreamingExecutor.submit(() -> {
            boolean acquired = false;
            ScheduledExecutorService heartbeatScheduler = null;
            AtomicBoolean isFinished = new AtomicBoolean(false);

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
            boolean wasFallbackTriggered = false;
            boolean usedPersonalTool = false;
            boolean usedTrendingSongsTool = false;
            boolean hasSideEffectToolExecuted = false;
            int executedToolsCount = 0;

            // 2. Quản lý Permit cho Reasoning Tier
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(ModelRouterService.CIRCUIT_BREAKER_INSTANCE);
            boolean permitAcquired = false;
            AtomicBoolean resultRecorded = new AtomicBoolean(false);
            long permitStartNs = System.nanoTime();

            if (routingDecision.tier() == ModelTier.REASONING_TIER) {
                if (!circuitBreaker.tryAcquirePermission()) {
                    log.warn("[Agent Orchestrator] Circuit Breaker không cấp permit. Hạ cấp tức thì sang {}", routerProperties.getFastTierModel());
                    currentTargetModel = routerProperties.getFastTierModel();
                    degradedByBreaker = true;
                } else {
                    permitAcquired = true;
                }
            }

            // Trạng thái theo lượt (Turn State)
            Set<String> turnAllowedSongIds = new HashSet<>();
            Map<String, SongCardDto> retrievedSongsMap = new LinkedHashMap<>();
            List<SongCardDto> suggestedSongsList = new ArrayList<>();
            PlayerActionDto resolvedPlayerAction = null;
            PlaylistProposalDto resolvedPlaylistProposal = null;

            StringBuilder fullTextBuilder = new StringBuilder();
            AtomicInteger totalTokensHolder = new AtomicInteger(0);

            try {
                acquired = aiStreamingSemaphore.tryAcquire(5, TimeUnit.SECONDS);
                if (!acquired) {
                    log.warn("[Agent Orchestrator] Đạt giới hạn 50 luồng đồng thời. Từ chối.");
                    sendErrorPacket(emitter, "AI_SERVER_BUSY");
                    if (onCompleteCallback != null) {
                        onCompleteCallback.accept(AgentTurnResult.failed("AI_SERVER_BUSY"));
                    }
                    emitter.complete();
                    return;
                }

                // Heartbeat mỗi 15 giây
                heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
                heartbeatScheduler.scheduleAtFixedRate(() -> {
                    if (!isFinished.get()) {
                        try {
                            emitter.send(SseEmitter.event().comment("heartbeat"));
                        } catch (Exception ignored) {}
                    }
                }, 15, 15, TimeUnit.SECONDS);

                // Khởi tạo multi-turn contents
                List<Map<String, Object>> contents = new ArrayList<>(initialContents);
                List<Map<String, Object>> tools = toolDeclarationFactory.buildToolDeclarations();

                int currentIteration = 0;
                boolean shouldContinueLoop = true;

                while (shouldContinueLoop && currentIteration < MAX_TOOL_ITERATIONS) {
                    currentIteration++;
                    log.info("[Agent Orchestrator] Bắt đầu Iteration {}/{}, Model={}", currentIteration, MAX_TOOL_ITERATIONS, currentTargetModel);

                    Map<String, Object> requestPayload = new LinkedHashMap<>();
                    if (systemInstruction != null && !systemInstruction.isBlank()) {
                        requestPayload.put("system_instruction", Map.of(
                                "parts", List.of(Map.of("text", systemInstruction))
                        ));
                    }
                    requestPayload.put("contents", contents);

                    if (currentIteration < MAX_TOOL_ITERATIONS) {
                        requestPayload.put("tools", tools);
                    }

                    Map<String, Object> generationConfig = new LinkedHashMap<>();
                    generationConfig.put("temperature", 0.7);
                    generationConfig.put("maxOutputTokens", 800);
                    requestPayload.put("generationConfig", generationConfig);

                    String requestJson = objectMapper.writeValueAsString(requestPayload);
                    String endpoint = "/models/" + currentTargetModel + ":generateContent";

                    String responseJson;
                    long callStartNs = System.nanoTime();
                    try {
                        responseJson = callWithWatchdog(endpoint, requestJson, routerProperties.getFirstTokenTimeoutMs());
                        long callDurationNs = System.nanoTime() - callStartNs;
                        if (meterRegistry != null) {
                            meterRegistry.timer("ai.latency.ttft", "model", currentTargetModel).record(callDurationNs, TimeUnit.NANOSECONDS);
                        }
                    } catch (Exception callEx) {
                        log.error("[Agent Orchestrator] Lỗi khi gọi Gemini (Model={}): {}", currentTargetModel, callEx.getMessage());

                        if (meterRegistry != null && callEx.getMessage() != null) {
                            if (callEx.getMessage().contains("400")) {
                                meterRegistry.counter("ai.gemini.client.errors", "status", "400").increment();
                            } else if (callEx.getMessage().contains("403")) {
                                meterRegistry.counter("ai.gemini.client.errors", "status", "403").increment();
                            }
                        }

                        // Phân biệt lỗi Client Abort
                        if (isClientAbort(callEx)) {
                            if (permitAcquired && !resultRecorded.get()) {
                                circuitBreaker.releasePermission();
                                resultRecorded.set(true);
                            }
                            throw callEx;
                        }

                        // Bọc lỗi upstream vào UpstreamAiException để đảm bảo Resilience4j record-exceptions ghi nhận
                        UpstreamAiException upstreamEx = (callEx instanceof UpstreamAiException u)
                                ? u
                                : new UpstreamAiException("Lỗi giao tiếp upstream Gemini: " + callEx.getMessage(), callEx);

                        if (permitAcquired && !resultRecorded.get()) {
                            circuitBreaker.onError(System.nanoTime() - permitStartNs, TimeUnit.NANOSECONDS, upstreamEx);
                            resultRecorded.set(true);
                        }

                        // Fallback logic: Cho phép fallback khi chưa có công cụ side-effect (player/playlist) nào thực thi
                        if (!hasSideEffectToolExecuted && !wasFallbackTriggered && routingDecision.tier() == ModelTier.REASONING_TIER) {
                            log.warn("[Agent Orchestrator] Kích hoạt Fallback từ {} sang {}", currentTargetModel, routerProperties.getFastTierModel());
                            wasFallbackTriggered = true;
                            String fromModel = currentTargetModel;
                            currentTargetModel = routerProperties.getFastTierModel();
                            if (meterRegistry != null) {
                                meterRegistry.counter("ai.router.fallbacks", "from", fromModel, "to", currentTargetModel).increment();
                            }
                            // Thử lại với fastTierModel
                            endpoint = "/models/" + currentTargetModel + ":generateContent";
                            responseJson = callWithWatchdog(endpoint, requestJson, routerProperties.getFirstTokenTimeoutMs());
                        } else {
                            if (hasSideEffectToolExecuted) {
                                log.error("[Agent Orchestrator] Công cụ side-effect (player/playlist) đã thực thi, CẤM fallback sang mô hình khác. Dừng an toàn với AI_SERVER_BUSY.");
                                sendErrorPacket(emitter, "AI_SERVER_BUSY");
                                if (onCompleteCallback != null) {
                                    onCompleteCallback.accept(AgentTurnResult.failed("AI_SERVER_BUSY"));
                                }
                                return;
                            }
                            throw upstreamEx;
                        }
                    }

                    if (responseJson == null || responseJson.isBlank()) {
                        log.warn("[Agent Orchestrator] Gemini phản hồi rỗng tại iteration {}", currentIteration);
                        break;
                    }

                    JsonNode root = objectMapper.readTree(responseJson);

                    // Ghi nhận Safety Blocks từ PromptFeedback (nếu có)
                    JsonNode promptFeedback = root.path("promptFeedback");
                    if (!promptFeedback.isMissingNode() && promptFeedback.has("blockReason")) {
                        String blockReason = promptFeedback.path("blockReason").asText();
                        Optional<SafetyBlockCategory> categoryOpt = SafetyBlockCategory.fromPromptBlockReason(blockReason);
                        categoryOpt.ifPresent(category -> {
                            if (meterRegistry != null) {
                                meterRegistry.counter("ai.gemini.safety.blocks", "category", category.name()).increment();
                            }
                        });
                    }

                    // Đọc token usage
                    JsonNode usageNode = root.path("usageMetadata");
                    if (!usageNode.isMissingNode() && usageNode.has("totalTokenCount")) {
                        totalTokensHolder.set(usageNode.path("totalTokenCount").asInt());
                    }

                    JsonNode candidates = root.path("candidates");
                    if (candidates.isMissingNode() || !candidates.isArray() || candidates.isEmpty()) {
                        log.warn("[Agent Orchestrator] Không có candidate nào trong phản hồi Gemini");
                        break;
                    }

                    // Ghi nhận Safety Blocks từ Candidate finishReason (nếu có)
                    String finishReason = candidates.get(0).path("finishReason").asText(null);
                    if (finishReason != null) {
                        Optional<SafetyBlockCategory> candidateCategoryOpt = SafetyBlockCategory.fromCandidateFinishReason(finishReason);
                        candidateCategoryOpt.ifPresent(category -> {
                            if (meterRegistry != null) {
                                meterRegistry.counter("ai.gemini.safety.blocks", "category", category.name()).increment();
                            }
                        });
                    }

                    JsonNode contentNode = candidates.get(0).path("content");
                    JsonNode partsNode = contentNode.path("parts");

                    // 2. Kiểm tra xem có functionCall trong parts hay không
                    List<JsonNode> functionCallParts = new ArrayList<>();
                    StringBuilder textPartsBuilder = new StringBuilder();

                    if (partsNode.isArray()) {
                        for (JsonNode part : partsNode) {
                            if (part.has("functionCall")) {
                                functionCallParts.add(part.get("functionCall"));
                            } else if (part.has("text")) {
                                textPartsBuilder.append(part.get("text").asText(""));
                            }
                        }
                    }

                    if (functionCallParts.isEmpty()) {
                        // Không có Function Call -> Gemini đã sinh câu trả lời văn bản cuối cùng!
                        shouldContinueLoop = false;
                        String finalText = textPartsBuilder.toString();
                        fullTextBuilder.append(finalText);

                        // Phát các text chunks xuống SSE cho client
                        emitTextChunks(emitter, finalText);

                    } else {
                        // 3. Có một hoặc nhiều Function Calls (song song)
                        log.info("[Agent Orchestrator] Nhận được {} function call(s) từ Gemini", functionCallParts.size());

                        Map<String, Object> modelTurnBlock = new LinkedHashMap<>();
                        modelTurnBlock.put("role", "model");

                        List<Map<String, Object>> modelPartsList = objectMapper.convertValue(
                                partsNode,
                                new TypeReference<List<Map<String, Object>>>() {}
                        );
                        modelTurnBlock.put("parts", modelPartsList);
                        contents.add(modelTurnBlock);

                        // 4. Thực thi từng Function Call và thu thập Function Responses
                        List<Map<String, Object>> functionResponseParts = new ArrayList<>();

                        for (JsonNode fc : functionCallParts) {
                            executedToolsCount++;
                            String toolName = fc.path("name").asText("");
                            JsonNode argsNode = fc.path("args");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);

                            if (GeminiToolDeclarationFactory.TOOL_TRIGGER_PLAYER_ACTION.equals(toolName)
                                    || GeminiToolDeclarationFactory.TOOL_PROPOSE_AI_PLAYLIST.equals(toolName)) {
                                hasSideEffectToolExecuted = true;
                            }

                            if (GeminiToolDeclarationFactory.TOOL_GET_USER_RECOMMENDATIONS.equals(toolName)) {
                                usedPersonalTool = true;
                            }

                            if (GeminiToolDeclarationFactory.TOOL_GET_TOP_TRENDING.equals(toolName)) {
                                usedTrendingSongsTool = true;
                            }

                            // Bắn sự kiện tool_status xuống client
                            sendToolStatusEvent(emitter, toolName);

                            // Thực thi Tool an toàn qua MusicToolExecutor
                            MusicToolExecutor.ToolExecutionResult execResult = musicToolExecutor.executeTool(
                                    toolName, args, currentUsername, userRawPrompt,
                                    turnAllowedSongIds, retrievedSongsMap
                            );

                            if (execResult.playerAction() != null) {
                                resolvedPlayerAction = execResult.playerAction();
                            }
                            if (execResult.playlistProposal() != null) {
                                resolvedPlaylistProposal = execResult.playlistProposal();
                            }
                            if (execResult.newSuggestedSongs() != null) {
                                for (SongCardDto sc : execResult.newSuggestedSongs()) {
                                    if (suggestedSongsList.stream().noneMatch(x -> x.getId().equals(sc.getId()))) {
                                        suggestedSongsList.add(sc);
                                    }
                                }
                            }

                            // Đóng gói Function Response
                            Map<String, Object> frWrapper = Map.of(
                                    "functionResponse", Map.of(
                                            "name", toolName,
                                            "response", execResult.responsePayload()
                                    )
                            );
                            functionResponseParts.add(frWrapper);
                        }

                        // Thêm turn kết quả tool vào contents cho iteration sau
                        Map<String, Object> functionTurnBlock = new LinkedHashMap<>();
                        functionTurnBlock.put("role", "user");
                        functionTurnBlock.put("parts", functionResponseParts);
                        contents.add(functionTurnBlock);
                    }
                }

                // Ghi nhận Breaker onSuccess nếu đang giữ permit
                if (permitAcquired && !resultRecorded.get()) {
                    circuitBreaker.onSuccess(System.nanoTime() - permitStartNs, TimeUnit.NANOSECONDS);
                    resultRecorded.set(true);
                }

                // 5. Kết thúc lượt: Gửi gói tin hoàn tất (Complete Packet)
                if (!isFinished.get()) {
                    Map<String, Object> completePacket = new LinkedHashMap<>();
                    completePacket.put("delta", "");
                    completePacket.put("done", true);
                    completePacket.put("status", "COMPLETE");
                    completePacket.put("totalTokens", totalTokensHolder.get());
                    completePacket.put("suggestedSongs", suggestedSongsList);
                    completePacket.put("playerAction", resolvedPlayerAction);
                    completePacket.put("playlistProposal", resolvedPlaylistProposal);

                    String completeJson = objectMapper.writeValueAsString(completePacket);
                    emitter.send(SseEmitter.event().data(completeJson));
                    emitter.complete();

                    log.info("[Agent Orchestrator] Hoàn tất lượt Agent: model={}, tokens={}, songs={}",
                            currentTargetModel, totalTokensHolder.get(), suggestedSongsList.size());

                    if (onCompleteCallback != null) {
                        onCompleteCallback.accept(AgentTurnResult.success(
                                fullTextBuilder.toString(),
                                totalTokensHolder.get(),
                                suggestedSongsList,
                                resolvedPlayerAction,
                                resolvedPlaylistProposal,
                                currentTargetModel,
                                usedPersonalTool,
                                usedTrendingSongsTool,
                                wasFallbackTriggered,
                                degradedByBreaker
                        ));
                    }
                }

            } catch (Exception e) {
                log.error("[Agent Orchestrator] Lỗi trong vòng lặp Agent: {}", e.getMessage(), e);

                if (isClientAbort(e)) {
                    if (permitAcquired && !resultRecorded.get()) {
                        circuitBreaker.releasePermission();
                        resultRecorded.set(true);
                    }
                } else {
                    if (permitAcquired && !resultRecorded.get()) {
                        UpstreamAiException upstreamEx = (e instanceof UpstreamAiException u)
                                ? u
                                : new UpstreamAiException("Lỗi giao tiếp upstream Gemini: " + e.getMessage(), e);
                        circuitBreaker.onError(System.nanoTime() - permitStartNs, TimeUnit.NANOSECONDS, upstreamEx);
                        resultRecorded.set(true);
                    }
                }

                sendErrorPacket(emitter, "AI_STREAM_INTERRUPTED");

                if (onCompleteCallback != null) {
                    onCompleteCallback.accept(new AgentTurnResult(
                            fullTextBuilder.toString(),
                            totalTokensHolder.get(),
                            (fullTextBuilder.length() > 0) ? MessageStatus.INTERRUPTED : MessageStatus.FAILED,
                            suggestedSongsList,
                            resolvedPlayerAction,
                            resolvedPlaylistProposal,
                            e.getMessage(),
                            usedPersonalTool,
                            usedTrendingSongsTool,
                            wasFallbackTriggered,
                            degradedByBreaker,
                            currentTargetModel
                    ));
                }
            } finally {
                if (permitAcquired && !resultRecorded.get()) {
                    circuitBreaker.releasePermission();
                    resultRecorded.set(true);
                }
                isFinished.set(true);
                if (heartbeatScheduler != null) {
                    heartbeatScheduler.shutdownNow();
                }
                if (acquired) {
                    aiStreamingSemaphore.release();
                }
            }
        });
    }

    private String callWithWatchdog(String endpoint, String requestJson, long timeoutMs) throws Exception {
        if (geminiRestClient == null) {
            return null;
        }

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                geminiRestClient.post()
                        .uri(endpoint)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestJson)
                        .retrieve()
                        .body(String.class),
                aiStreamingExecutor
        );

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new FirstTokenTimeoutException("Watchdog timeout sau " + timeoutMs + "ms không nhận được phản hồi", te);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    private boolean isClientAbort(Throwable t) {
        while (t != null) {
            String className = t.getClass().getName();
            if (className.contains("ClientAbortException") || className.contains("AsyncRequestNotUsableException")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private void sendToolStatusEvent(SseEmitter emitter, String toolName) {
        try {
            String message = getToolStatusDescription(toolName);
            Map<String, Object> statusPacket = Map.of(
                    "type", "tool_status",
                    "toolName", toolName,
                    "message", message
            );
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(statusPacket)));
        } catch (Exception e) {
            log.debug("[Agent Orchestrator] Không thể gửi tool_status: {}", e.getMessage());
        }
    }

    private String getToolStatusDescription(String toolName) {
        return switch (toolName) {
            case GeminiToolDeclarationFactory.TOOL_SEARCH_MUSIC_LIBRARY -> "Đang tìm kiếm bài hát trong kho nhạc...";
            case GeminiToolDeclarationFactory.TOOL_GET_TOP_TRENDING -> "Đang xem bảng xếp hạng thịnh hành...";
            case GeminiToolDeclarationFactory.TOOL_GET_USER_RECOMMENDATIONS -> "Đang phân tích gu âm nhạc của bạn...";
            case GeminiToolDeclarationFactory.TOOL_TRIGGER_PLAYER_ACTION -> "Đang chuẩn bị phát nhạc...";
            case GeminiToolDeclarationFactory.TOOL_PROPOSE_AI_PLAYLIST -> "Đang tạo danh sách phát đề xuất...";
            default -> "Đang xử lý dữ liệu âm nhạc...";
        };
    }

    private void emitTextChunks(SseEmitter emitter, String text) {
        if (text == null || text.isEmpty()) return;

        try {
            int chunkSize = 25;
            for (int i = 0; i < text.length(); i += chunkSize) {
                int end = Math.min(i + chunkSize, text.length());
                String delta = text.substring(i, end);

                Map<String, Object> chunkPacket = Map.of(
                        "delta", delta,
                        "done", false
                );
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunkPacket)));
                Thread.sleep(30);
            }
        } catch (Exception e) {
            log.debug("[Agent Orchestrator] Lỗi khi phát text chunks: {}", e.getMessage());
        }
    }

    private void sendErrorPacket(SseEmitter emitter, String errorCode) {
        try {
            Map<String, Object> errorPacket = Map.of(
                    "delta", "",
                    "done", true,
                    "status", "ERROR",
                    "errorCode", errorCode
            );
            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(errorPacket)));
            emitter.complete();
        } catch (Exception ignored) {}
    }
}
