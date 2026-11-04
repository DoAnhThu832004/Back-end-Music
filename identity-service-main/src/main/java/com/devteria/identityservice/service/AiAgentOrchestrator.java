package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.PlayerActionDto;
import com.devteria.identityservice.dto.response.PlaylistProposalDto;
import com.devteria.identityservice.dto.response.SongCardDto;
import com.devteria.identityservice.enums.MessageStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Bộ điều phối Multi-Step Agent Loop cho Gemini Function Calling (Task 3A.7).
 * - Giữ nguyên duy nhất 1 SseEmitter từ đầu đến cuối kết nối với Mobile App.
 * - Quản lý tối đa MAX_TOOL_ITERATIONS = 3 vòng lặp công cụ.
 * - Hỗ trợ thực thi nhiều functionCall song song trong một lượt phản hồi của LLM.
 * - Bắn sự kiện tool_status theo thời gian thực xuống client.
 * - Duy trì turnAllowedSongIds chống ảo giác ID bài hát.
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
            String errorMessage
    ) {}

    @Autowired
    public AiAgentOrchestrator(
            @Qualifier("geminiRestClient") RestClient geminiRestClient,
            @Qualifier("geminiStreamRestClient") RestClient geminiStreamRestClient,
            ObjectMapper objectMapper,
            GeminiToolDeclarationFactory toolDeclarationFactory,
            MusicToolExecutor musicToolExecutor,
            @Qualifier("aiStreamingExecutor") ExecutorService aiStreamingExecutor,
            @Qualifier("aiStreamingSemaphore") Semaphore aiStreamingSemaphore) {
        this.geminiRestClient = geminiRestClient;
        this.geminiStreamRestClient = geminiStreamRestClient != null ? geminiStreamRestClient : geminiRestClient;
        this.objectMapper = objectMapper;
        this.toolDeclarationFactory = toolDeclarationFactory;
        this.musicToolExecutor = musicToolExecutor;
        this.aiStreamingExecutor = aiStreamingExecutor != null ? aiStreamingExecutor : Executors.newVirtualThreadPerTaskExecutor();
        this.aiStreamingSemaphore = aiStreamingSemaphore != null ? aiStreamingSemaphore : new Semaphore(50);
    }

    /**
     * Điều phối toàn bộ một lượt tương tác Agentic Chat trên luồng SSE.
     */
    public void orchestrateTurn(
            String currentUsername,
            String userRawPrompt,
            List<Map<String, Object>> initialContents,
            String systemInstruction,
            SseEmitter emitter,
            Consumer<AgentTurnResult> onCompleteCallback) {

        aiStreamingExecutor.submit(() -> {
            boolean acquired = false;
            ScheduledExecutorService heartbeatScheduler = null;
            AtomicBoolean isFinished = new AtomicBoolean(false);

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
                        onCompleteCallback.accept(new AgentTurnResult("", 0, MessageStatus.FAILED,
                                Collections.emptyList(), null, null, "AI_SERVER_BUSY"));
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
                    log.info("[Agent Orchestrator] Bắt đầu Iteration {}/{}", currentIteration, MAX_TOOL_ITERATIONS);

                    // 1. Chuẩn bị payload gọi Gemini (nếu là vòng lặp cuối cùng, không kèm tools để buộc sinh text)
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
                    String endpoint = "/models/" + model + ":generateContent";

                    String responseJson = geminiRestClient.post()
                            .uri(endpoint)
                            .header("x-goog-api-key", apiKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(requestJson)
                            .retrieve()
                            .body(String.class);

                    if (responseJson == null || responseJson.isBlank()) {
                        log.warn("[Agent Orchestrator] Gemini phản hồi rỗng tại iteration {}", currentIteration);
                        break;
                    }

                    JsonNode root = objectMapper.readTree(responseJson);

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

                        // Phát các text chunks xuống SSE cho client (giả lập mượt mà)
                        emitTextChunks(emitter, finalText);

                    } else {
                        // 3. Có một hoặc nhiều Function Calls (song song)
                        log.info("[Agent Orchestrator] Nhận được {} function call(s) từ Gemini", functionCallParts.size());

                        // Thêm turn phản hồi của Model vào contents
                        Map<String, Object> modelTurnBlock = new LinkedHashMap<>();
                        modelTurnBlock.put("role", "model");

                        List<Map<String, Object>> modelPartsList = new ArrayList<>();
                        for (JsonNode fc : functionCallParts) {
                            Map<String, Object> fcMap = objectMapper.convertValue(fc, Map.class);
                            modelPartsList.add(Map.of("functionCall", fcMap));
                        }
                        if (textPartsBuilder.length() > 0) {
                            modelPartsList.add(Map.of("text", textPartsBuilder.toString()));
                        }
                        modelTurnBlock.put("parts", modelPartsList);
                        contents.add(modelTurnBlock);

                        // 4. Thực thi từng Function Call và thu thập Function Responses
                        List<Map<String, Object>> functionResponseParts = new ArrayList<>();

                        for (JsonNode fc : functionCallParts) {
                            String toolName = fc.path("name").asText("");
                            JsonNode argsNode = fc.path("args");
                            Map<String, Object> args = objectMapper.convertValue(argsNode, Map.class);

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

                        // Thêm turn kết quả tool (role: "function") vào contents cho iteration sau
                        Map<String, Object> functionTurnBlock = new LinkedHashMap<>();
                        functionTurnBlock.put("role", "function");
                        functionTurnBlock.put("parts", functionResponseParts);
                        contents.add(functionTurnBlock);
                    }
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

                    log.info("[Agent Orchestrator] Hoàn tất lượt Agent: tokens={}, songs={}, playerAction={}",
                            totalTokensHolder.get(), suggestedSongsList.size(),
                            resolvedPlayerAction != null ? resolvedPlayerAction.getAction() : "none");

                    if (onCompleteCallback != null) {
                        onCompleteCallback.accept(new AgentTurnResult(
                                fullTextBuilder.toString(),
                                totalTokensHolder.get(),
                                MessageStatus.COMPLETE,
                                suggestedSongsList,
                                resolvedPlayerAction,
                                resolvedPlaylistProposal,
                                null
                        ));
                    }
                }

            } catch (Exception e) {
                log.error("[Agent Orchestrator] Lỗi trong vòng lặp Agent: {}", e.getMessage(), e);
                sendErrorPacket(emitter, "AI_STREAM_INTERRUPTED");

                if (onCompleteCallback != null) {
                    onCompleteCallback.accept(new AgentTurnResult(
                            fullTextBuilder.toString(),
                            totalTokensHolder.get(),
                            (fullTextBuilder.length() > 0) ? MessageStatus.INTERRUPTED : MessageStatus.FAILED,
                            suggestedSongsList,
                            resolvedPlayerAction,
                            resolvedPlaylistProposal,
                            e.getMessage()
                    ));
                }
            } finally {
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
            log.debug("[Agent Orchestrator] Không thể gửi tool_status (client có thể đã đóng kết nối): {}", e.getMessage());
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
            // Chia nhỏ text thành các đoạn (chunks) khoảng 20-30 ký tự để client hiển thị hiệu ứng gõ mượt mà
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
