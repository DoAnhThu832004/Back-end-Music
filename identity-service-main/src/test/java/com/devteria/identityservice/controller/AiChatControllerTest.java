package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.AiChatStreamRequest;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AiChatController aiChatController;

    private User sampleUser;
    private User otherUser;
    private ChatSession sampleSession;

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
    @DisplayName("streamChat thành công -> lưu tin nhắn USER, khởi tạo stream và trả về SseEmitter")
    void streamChat_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
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
        // Kiểm tra lưu tin nhắn USER vào DB trước khi stream
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        // Kiểm tra kiểm duyệt async
        verify(aiModerationService, times(1)).moderateUserChatAsync(eq("Xin chào"), eq(sampleUser), any());
        // Kiểm tra mở luồng stream với Gemini
        verify(aiAgentOrchestrator, times(1)).orchestrateTurn(eq("testuser"), eq("Xin chào"), any(), any(), eq(emitter), any());
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
        verify(aiAgentOrchestrator, never()).orchestrateTurn(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ném AI_SESSION_BUSY (409) khi session đang có stream khác xử lý dở")
    void streamChat_SessionBusy() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        doThrow(new AppException(ErrorCode.AI_SESSION_BUSY))
                .when(aiChatGuardService).checkAndLockSession("session-1");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Tin nhắn spam")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SESSION_BUSY);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném AI_RATE_LIMIT_EXCEEDED (429) khi user vượt hạn mức tần suất")
    void streamChat_RateLimitExceeded() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(sampleSession));
        doThrow(new AppException(ErrorCode.AI_RATE_LIMIT_EXCEEDED))
                .when(aiChatGuardService).validateAndRecordUserRequest("user-123");

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("session-1")
                .message("Tin nhắn quá nhanh")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_RATE_LIMIT_EXCEEDED);

        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném SESSION_NOT_EXISTED khi không tìm thấy session")
    void streamChat_SessionNotExisted() {
        mockAuthentication();
        when(chatSessionRepository.findById("non-existent")).thenReturn(Optional.empty());

        AiChatStreamRequest request = AiChatStreamRequest.builder()
                .sessionId("non-existent")
                .message("Chào")
                .build();

        assertThatThrownBy(() -> aiChatController.streamChat(request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_EXISTED);
    }
}
