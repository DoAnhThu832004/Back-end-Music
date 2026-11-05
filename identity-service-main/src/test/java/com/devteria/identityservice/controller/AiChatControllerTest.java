package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.AiChatStreamRequest;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SongRepository songRepository;

    @Mock
    private AiChatGuardService aiChatGuardService;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private AiPersonaPromptFactory promptFactory;

    @Mock
    private AiAgentOrchestrator aiAgentOrchestrator;

    @Mock
    private AiModerationService aiModerationService;

    @Mock
    private AiChatSessionService aiChatSessionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AiChatController aiChatController;

    private User sampleUser;
    private User otherUser;
    private ChatSession sampleSession;
    private Song sampleSong;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id("user-123")
                .username("testuser")
                .build();

        otherUser = User.builder()
                .id("user-999")
                .username("otheruser")
                .build();

        sampleSession = ChatSession.builder()
                .id("session-1")
                .user(sampleUser)
                .title("Cuộc trò chuyện mới")
                .persona(AiPersona.GENZ_DJ)
                .build();

        sampleSong = new Song();
        sampleSong.setId("song-123");
        sampleSong.setName("Lạc Trôi");

        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthentication() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));
    }

    @Test
    @DisplayName("streamChat thành công -> câu hỏi LLM chuẩn vượt qua Fast-Path, lưu tin nhắn USER, khởi tạo stream và gọi orchestrator")
    void streamChat_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(false);
        when(conversationMemoryManager.getSlidingWindowHistory("session-1")).thenReturn(Collections.emptyList());
        when(promptFactory.buildSafeUserPrompt("Gợi ý cho tôi danh sách bài hát ballad"))
                .thenReturn("<user_content>Gợi ý cho tôi danh sách bài hát ballad</user_content>");
        when(conversationMemoryManager.buildGeminiContents(any(), any())).thenReturn(List.of());
        when(promptFactory.buildSystemInstruction(eq(AiPersona.GENZ_DJ), any())).thenReturn("System prompt");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Gợi ý cho tôi danh sách bài hát ballad")
                .currentSongId(null)
                .build();

        SseEmitter emitter = aiChatController.streamChat(request);

        assertThat(emitter).isNotNull();
        // Kiểm tra đã kiểm tra và khóa phiên cho luồng LLM
        verify(aiChatGuardService, times(1)).checkAndLockSession("session-1");
        // Kiểm tra lưu tin nhắn USER vào DB trước khi stream
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        // Kiểm tra kiểm duyệt async
        verify(aiModerationService, times(1)).moderateUserChatAsync(eq("Gợi ý cho tôi danh sách bài hát ballad"), eq(sampleUser), any());
        // Kiểm tra mở luồng stream với Gemini (9 tham số)
        verify(aiAgentOrchestrator, times(1)).orchestrateTurn(
                eq("testuser"),
                eq("Gợi ý cho tôi danh sách bài hát ballad"),
                any(),
                any(),
                eq(AiPersona.GENZ_DJ),
                isNull(),
                eq(false),
                eq(emitter),
                any()
        );
    }

    @Test
    @DisplayName("Fast-Path Greeting ở Turn 1 -> Phản hồi tức thì, cập nhật session title, không khóa session, không gọi orchestrator")
    void streamChat_FastPath_Greeting_Turn1_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(false); // Turn 1

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Xin chào")
                .currentSongId(null)
                .build();

        SseEmitter emitter = aiChatController.streamChat(request);

        assertThat(emitter).isNotNull();
        // Lưu tin nhắn USER và AI (2 lần)
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        // Cập nhật tiêu đề session
        verify(aiChatSessionService, times(1)).updateSessionTitleIfDefault("session-1", "Xin chào");
        // Tuyệt đối không lock session
        verify(aiChatGuardService, never()).checkAndLockSession(any());
        // Tuyệt đối không gọi LLM
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Fast-Path Greeting ở Turn 2+ -> Bỏ qua Fast-Path, rơi xuống luồng LLM bình thường")
    void streamChat_FastPath_Greeting_Turn2_Fallthrough() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(true); // Turn 2+
        when(conversationMemoryManager.getSlidingWindowHistory("session-1")).thenReturn(Collections.emptyList());
        when(promptFactory.buildSafeUserPrompt("Xin chào")).thenReturn("<user_content>Xin chào</user_content>");
        when(conversationMemoryManager.buildGeminiContents(any(), any())).thenReturn(List.of());
        when(promptFactory.buildSystemInstruction(eq(AiPersona.GENZ_DJ), any())).thenReturn("System prompt");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Xin chào")
                .currentSongId(null)
                .build();

        SseEmitter emitter = aiChatController.streamChat(request);

        assertThat(emitter).isNotNull();
        // Khóa phiên cho LLM
        verify(aiChatGuardService, times(1)).checkAndLockSession("session-1");
        // Gọi LLM
        verify(aiAgentOrchestrator, times(1)).orchestrateTurn(
                eq("testuser"),
                eq("Xin chào"),
                any(),
                any(),
                eq(AiPersona.GENZ_DJ),
                isNull(),
                eq(true),
                eq(emitter),
                any()
        );
    }

    @Test
    @DisplayName("Fast-Path Player Control -> Client hỗ trợ action & có bài đang phát -> Phản hồi PAUSE ngay trước khi lock session")
    void streamChat_FastPath_PlayerControl_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(songRepository.findById("song-123")).thenReturn(Optional.of(sampleSong));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(true);

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("tạm dừng")
                .currentSongId("song-123")
                .clientSupportsTransportActions(true)
                .build();

        SseEmitter emitter = aiChatController.streamChat(request);

        assertThat(emitter).isNotNull();
        // Lưu tin nhắn USER và AI
        verify(chatMessageRepository, times(2)).save(any(ChatMessage.class));
        // Không khóa session lock
        verify(aiChatGuardService, never()).checkAndLockSession(any());
        // Không gọi orchestrator
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Fast-Path Player Control -> Client cũ (clientSupportsTransportActions = false) -> Fallback xuống luồng LLM")
    void streamChat_FastPath_PlayerControl_LegacyClient_FallbackToLlm() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(songRepository.findById("song-123")).thenReturn(Optional.of(sampleSong));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(true);
        when(conversationMemoryManager.getSlidingWindowHistory("session-1")).thenReturn(Collections.emptyList());
        when(promptFactory.buildSafeUserPrompt("tạm dừng")).thenReturn("<user_content>tạm dừng</user_content>");
        when(conversationMemoryManager.buildGeminiContents(any(), any())).thenReturn(List.of());
        when(promptFactory.buildSystemInstruction(eq(AiPersona.GENZ_DJ), eq("song-123"))).thenReturn("System prompt");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("tạm dừng")
                .currentSongId("song-123")
                .clientSupportsTransportActions(false)
                .build();

        SseEmitter emitter = aiChatController.streamChat(request);

        assertThat(emitter).isNotNull();
        // Fallback: Khóa phiên và gọi LLM
        verify(aiChatGuardService, times(1)).checkAndLockSession("session-1");
        verify(aiAgentOrchestrator, times(1)).orchestrateTurn(
                eq("testuser"),
                eq("tạm dừng"),
                any(),
                any(),
                eq(AiPersona.GENZ_DJ),
                eq("song-123"),
                eq(true),
                eq(emitter),
                any()
        );
    }

    @Test
    @DisplayName("Chặn IDOR khi truy cập session của người dùng khác")
    void streamChat_Forbidden_IDOR() {
        mockAuthentication();

        ChatSession othersSession = ChatSession.builder()
                .id("session-other")
                .user(otherUser)
                .title("Session khác")
                .persona(AiPersona.GENZ_DJ)
                .build();

        when(chatSessionRepository.findById("session-other")).thenReturn(Optional.of(othersSession));

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-other")
                .message("Hack thử")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verify(chatMessageRepository, never()).save(any());
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Ném AI_SESSION_BUSY (409) khi session đang có stream khác xử lý dở")
    void streamChat_SessionBusy() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        when(chatMessageRepository.existsBySessionId("session-1")).thenReturn(true);
        doThrow(new AppException(ErrorCode.AI_SESSION_BUSY))
                .when(aiChatGuardService).checkAndLockSession("session-1");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Tin nhắn kế")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SESSION_BUSY);

        verify(chatMessageRepository, never()).save(any());
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Chặn request khi vượt Rate Limit người dùng (429)")
    void streamChat_RateLimitExceeded() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        doThrow(new AppException(ErrorCode.AI_RATE_LIMIT_EXCEEDED))
                .when(aiChatGuardService).validateAndRecordUserRequest("user-123");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Spam tin nhắn")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_RATE_LIMIT_EXCEEDED);

        verify(chatMessageRepository, never()).save(any());
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Ném SESSION_NOT_EXISTED (404) khi sessionId không tồn tại trong hệ thống")
    void streamChat_SessionNotExisted() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-none")).thenReturn(Optional.empty());

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-none")
                .message("Hello")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_EXISTED);

        verify(chatMessageRepository, never()).save(any());
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any());
    }
}
