package com.devteria.identityservice.controller;

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
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

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
    private final GeminiClientService geminiClientService;
    private final AiModerationService aiModerationService;
    private final AiChatSessionService aiChatSessionService;

    public AiChatController(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserRepository userRepository,
            SongRepository songRepository,
            AiChatGuardService aiChatGuardService,
            ConversationMemoryManager conversationMemoryManager,
            AiPersonaPromptFactory promptFactory,
            GeminiClientService geminiClientService,
            AiModerationService aiModerationService,
            AiChatSessionService aiChatSessionService) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.aiChatGuardService = aiChatGuardService;
        this.conversationMemoryManager = conversationMemoryManager;
        this.promptFactory = promptFactory;
        this.geminiClientService = geminiClientService;
        this.aiModerationService = aiModerationService;
        this.aiChatSessionService = aiChatSessionService;
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

        // 3. Khóa phiên Concurrency Session Lock (Chống spam song song)
        aiChatGuardService.checkAndLockSession(session.getId());

        // 4. Lấy thông tin bài hát hiện tại nếu có
        Song currentSong = (request.currentSongId() != null && !request.currentSongId().isBlank())
                ? songRepository.findById(request.currentSongId()).orElse(null)
                : null;

        // 5. Lấy lịch sử hội thoại trước khi lưu câu hỏi mới (tránh duplicate)
        List<ChatMessage> history = conversationMemoryManager.getSlidingWindowHistory(session.getId());

        // 6. Lưu tin nhắn USER vào DB trước khi mở luồng stream
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content(request.message().trim())
                .currentSong(currentSong)
                .tokenCount(0)
                .build();
        chatMessageRepository.save(userMessage);

        // 7. Tự động cập nhật tiêu đề session từ câu hỏi đầu tiên nếu còn là mặc định
        aiChatSessionService.updateSessionTitleIfDefault(session.getId(), request.message());

        // 8. Kích hoạt bất đồng bộ kiểm duyệt nội dung (Fail-Open, không chặn chat)
        aiModerationService.moderateUserChatAsync(request.message(), currentUser, request.currentSongId());

        // 9. Chuẩn bị ngữ cảnh hội thoại (Sliding Window & Prompt Grounding)
        String safePrompt = promptFactory.buildSafeUserPrompt(request.message());
        List<Map<String, Object>> contents = conversationMemoryManager.buildGeminiContents(history, safePrompt);
        String systemInstruction = promptFactory.buildSystemInstruction(session.getPersona(), request.currentSongId());

        // 10. Khởi tạo SseEmitter với timeout 90 giây
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

        // 11. Mở luồng stream Non-blocking với Gemini
        geminiClientService.streamGenerateContent(
                contents,
                systemInstruction,
                emitter,
                streamResult -> {
                    try {
                        // Nếu stream lỗi ngay từ đầu và không có nội dung, không lưu tin nhắn rỗng vào DB
                        if (streamResult.status() == MessageStatus.FAILED && (streamResult.fullContent() == null || streamResult.fullContent().isBlank())) {
                            log.warn("[AI Chat] Luồng AI lỗi trước khi sinh nội dung cho session {}, không lưu tin nhắn rỗng vào DB.", session.getId());
                            return;
                        }

                        // Lưu câu trả lời của AI vào DB (COMPLETE hoặc INTERRUPTED)
                        ChatMessage aiMessage = ChatMessage.builder()
                                .session(session)
                                .sender(MessageSender.AI)
                                .status(streamResult.status())
                                .content(streamResult.fullContent())
                                .currentSong(currentSong)
                                .tokenCount(streamResult.totalTokens())
                                .build();
                        chatMessageRepository.save(aiMessage);
                        log.info("[AI Chat] Đã lưu tin nhắn AI cho session {}: status={}, tokens={}",
                                session.getId(), streamResult.status(), streamResult.totalTokens());
                    } catch (Exception e) {
                        log.error("[AI Chat] Lỗi khi lưu câu trả lời AI vào DB: {}", e.getMessage(), e);
                    } finally {
                        aiChatGuardService.releaseSessionLock(session.getId());
                    }
                }
        );

        return emitter;
    }
}
