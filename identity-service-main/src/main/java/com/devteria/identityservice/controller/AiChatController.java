package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.FastPathResult;
import com.devteria.identityservice.dto.request.AiChatStreamRequest;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/ai/chat")
@Slf4j
public class AiChatController {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final AiChatGuardService aiChatGuardService;
    private final ConversationMemoryManager conversationMemoryManager;
    private final AiPersonaPromptFactory promptFactory;
    private final AiAgentOrchestrator aiAgentOrchestrator;
    private final AiModerationService aiModerationService;
    private final AiChatSessionService aiChatSessionService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Value("${ai.router.fast-path-enabled:true}")
    private boolean fastPathEnabled = true;

    public AiChatController(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            SongRepository songRepository,
            AiChatGuardService aiChatGuardService,
            ConversationMemoryManager conversationMemoryManager,
            AiPersonaPromptFactory promptFactory,
            AiAgentOrchestrator aiAgentOrchestrator,
            AiModerationService aiModerationService,
            AiChatSessionService aiChatSessionService,
            ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.aiChatGuardService = aiChatGuardService;
        this.conversationMemoryManager = conversationMemoryManager;
        this.promptFactory = promptFactory;
        this.aiAgentOrchestrator = aiAgentOrchestrator;
        this.aiModerationService = aiModerationService;
        this.aiChatSessionService = aiChatSessionService;
        this.objectMapper = objectMapper;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody @Valid AiChatStreamRequest request) {
        User currentUser = getAuthenticatedUser();

        // 1. Kiểm tra session và quyền sở hữu (IDOR)
        ChatSession session = chatSessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_EXISTED));

        if (!session.getUser().getId().equals(currentUser.getId())) {
            log.warn("[AI Chat] Người dùng {} cố tình truy cập trái phép session {} (IDOR)", currentUser.getUsername(), request.sessionId());
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 2. Kiểm tra Rate Limiting & Quota người dùng
        aiChatGuardService.validateAndRecordUserRequest(currentUser.getId());

        // Lấy thông tin bài hát hiện tại nếu có (cần cho cả Fast-Path lẫn LLM)
        Song currentSong = (request.currentSongId() != null && !request.currentSongId().isBlank())
                ? songRepository.findById(request.currentSongId()).orElse(null)
                : null;

        // 2.5. Kiểm tra Fast-Path TRƯỚC Concurrency Session Lock
        boolean hasPriorHistory = chatMessageRepository.existsBySessionId(session.getId());
        if (fastPathEnabled) {
            boolean clientSupportsTransport = Boolean.TRUE.equals(request.clientSupportsTransportActions());
            Optional<FastPathResult> fastResultOpt = FastPathEngine.evaluate(
                    request.message(),
                    session.getPersona(),
                    request.currentSongId(),
                    hasPriorHistory,
                    clientSupportsTransport
            );

            if (fastResultOpt.isPresent()) {
                log.info("[AI Chat] Yêu cầu khớp Fast-Path ({}) cho session {}. Bỏ qua Session Lock & LLM.",
                        fastResultOpt.get().type(), session.getId());
                return handleFastPathExecution(session, currentUser, currentSong, fastResultOpt.get(), request, hasPriorHistory);
            }
        }

        // 3. Khóa phiên Concurrency (Chặn 2 stream song song trên cùng 1 session)
        aiChatGuardService.checkAndLockSession(session.getId());

        // 4. Lấy lịch sử hội thoại trước khi lưu câu hỏi mới (tránh duplicate)
        List<ChatMessage> history = conversationMemoryManager.getSlidingWindowHistory(session.getId());

        // 5. Lưu tin nhắn USER vào DB trước khi mở luồng stream
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content(request.message().trim())
                .currentSong(currentSong)
                .tokenCount(0)
                .build();
        chatMessageRepository.save(userMessage);

        // 6. Tự động cập nhật tiêu đề session từ câu hỏi đầu tiên nếu còn là mặc định
        aiChatSessionService.updateSessionTitleIfDefault(session.getId(), request.message());

        // 7. Kích hoạt bất đồng bộ kiểm duyệt nội dung (Fail-Open, không chặn chat)
        aiModerationService.moderateUserChatAsync(request.message(), currentUser, request.currentSongId());

        // 8. Chuẩn bị ngữ cảnh hội thoại (Sliding Window & Prompt Grounding)
        String safePrompt = promptFactory.buildSafeUserPrompt(request.message());
        List<Map<String, Object>> contents = conversationMemoryManager.buildGeminiContents(history, safePrompt);
        String systemInstruction = promptFactory.buildSystemInstruction(session.getPersona(), request.currentSongId());

        // 9. Khởi tạo SseEmitter với timeout 90 giây
        SseEmitter emitter = new SseEmitter(90_000L);

        // Đăng ký callback dọn dẹp khóa session
        emitter.onCompletion(() -> aiChatGuardService.releaseSessionLock(session.getId()));
        emitter.onTimeout(() -> {
            log.warn("[AI Chat] SseEmitter timeout cho session {}", session.getId());
            aiChatGuardService.releaseSessionLock(session.getId());
            emitter.complete();
        });
        emitter.onError(e -> {
            log.warn("[AI Chat] SseEmitter error cho session {}: {}", session.getId(), e.getMessage());
            aiChatGuardService.releaseSessionLock(session.getId());
        });

        // 10. Mở luồng stream Non-blocking với Gemini kèm Model Routing & Breaker
        aiAgentOrchestrator.orchestrateTurn(
                currentUser.getUsername(),
                request.message(),
                contents,
                systemInstruction,
                session.getPersona(),
                request.currentSongId(),
                hasPriorHistory,
                emitter,
                turnResult -> {
                    try {
                        // Nếu stream lỗi ngay từ đầu và không có nội dung, không lưu tin nhắn rỗng vào DB
                        if (turnResult.status() == MessageStatus.FAILED && (turnResult.fullText() == null || turnResult.fullText().isBlank())) {
                            log.warn("[AI Chat] Luồng AI lỗi trước khi sinh nội dung cho session {}, không lưu tin nhắn rỗng vào DB.", session.getId());
                            return;
                        }

                        // Lưu câu trả lời của AI vào DB (COMPLETE hoặc INTERRUPTED)
                        ChatMessage aiMessage = ChatMessage.builder()
                                .session(session)
                                .sender(MessageSender.AI)
                                .status(turnResult.status())
                                .content(turnResult.fullText())
                                .currentSong(currentSong)
                                .tokenCount(turnResult.totalTokens())
                                .build();
                        chatMessageRepository.save(aiMessage);
                        log.info("[AI Chat] Đã lưu tin nhắn AI cho session {}: status={}, tokens={}",
                                session.getId(), turnResult.status(), turnResult.totalTokens());
                    } catch (Exception e) {
                        log.error("[AI Chat] Lỗi khi lưu câu trả lời AI vào DB: {}", e.getMessage(), e);
                    } finally {
                        aiChatGuardService.releaseSessionLock(session.getId());
                    }
                }
        );

        return emitter;
    }

    /**
     * Xử lý luồng phản hồi tức thì của Fast-Path:
     * - Đo đạc latency qua Timer ai.latency.fastpath & Counter ai.fastpath.hits.
     * - Lưu tin nhắn User và AI vào DB với timestamp tách biệt (+5ms).
     * - Trả về SseEmitter riêng biệt KHÔNG giải phóng Session Lock của luồng stream khác.
     */
    private SseEmitter handleFastPathExecution(
            ChatSession session,
            User currentUser,
            Song currentSong,
            FastPathResult fastResult,
            AiChatStreamRequest request,
            boolean hasPriorHistory) {

        Timer.Sample sample = (meterRegistry != null) ? Timer.start(meterRegistry) : null;

        // 1. Cập nhật tiêu đề session nếu là Turn 1
        if (!hasPriorHistory) {
            aiChatSessionService.updateSessionTitleIfDefault(session.getId(), request.message());
        }

        // 2. Lưu tin nhắn USER vào DB với timestamp T1
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
        chatMessageRepository.save(userMessage);

        // 3. Lưu tin nhắn AI Fast-Path vào DB với timestamp T1 + 5ms (đảm bảo thứ tự thời gian phân định)
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .sender(MessageSender.AI)
                .status(MessageStatus.COMPLETE)
                .content(fastResult.responseText())
                .currentSong(currentSong)
                .tokenCount(0)
                .build();
        aiMessage.setCreatedAt(userTimestamp.plus(5, ChronoUnit.MILLIS));
        chatMessageRepository.save(aiMessage);

        // 4. Khởi tạo Emitter riêng cho Fast-Path: KHÔNG đăng ký releaseSessionLock
        SseEmitter fastEmitter = new SseEmitter(10_000L);
        try {
            // Gửi delta chunk
            Map<String, Object> chunkPacket = Map.of(
                    "delta", fastResult.responseText(),
                    "done", false
            );
            fastEmitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunkPacket)));

            // Gửi complete packet
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
            log.info("[Fast-Path] Đã xử lý lệnh thành công cho session {}: type={}, text='{}'",
                    session.getId(), fastResult.type(), fastResult.responseText());
        } catch (IOException e) {
            log.warn("[Fast-Path] Lỗi khi gửi SSE gói tin Fast-Path cho session {}: {}", session.getId(), e.getMessage());
            fastEmitter.completeWithError(e);
        } finally {
            if (sample != null && meterRegistry != null) {
                sample.stop(meterRegistry.timer("ai.latency.fastpath"));
            }
            if (meterRegistry != null) {
                meterRegistry.counter("ai.fastpath.hits", "type", fastResult.type().name().toLowerCase(Locale.ROOT)).increment();
            }
        }
        return fastEmitter;
    }
}
