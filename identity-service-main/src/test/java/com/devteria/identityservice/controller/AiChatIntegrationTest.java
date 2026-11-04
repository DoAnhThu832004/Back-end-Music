package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.AiChatStreamRequest;
import com.devteria.identityservice.dto.request.ChatSessionCreationRequest;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.SongRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.AiChatGuardService;
import com.devteria.identityservice.service.GeminiClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
public class AiChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiChatGuardService aiChatGuardService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ChatSessionRepository chatSessionRepository;

    @MockBean
    private ChatMessageRepository chatMessageRepository;

    @MockBean
    private SongRepository songRepository;

    @MockBean
    private GeminiClientService geminiClientService;

    private User testUser;
    private User otherUser;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id("user-123")
                .username("testuser")
                .build();

        otherUser = User.builder()
                .id("user-999")
                .username("otheruser")
                .build();

        testSession = ChatSession.builder()
                .id("session-1")
                .user(testUser)
                .title("Cuộc trò chuyện mới")
                .persona(AiPersona.GENZ_DJ)
                .build();
        testSession.setCreatedAt(LocalDateTime.now());
        testSession.setUpdatedAt(LocalDateTime.now());

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.findByUsername("otheruser")).thenReturn(Optional.of(otherUser));
        when(chatSessionRepository.findById("session-1")).thenReturn(Optional.of(testSession));

        aiChatGuardService.resetLimitsForUser(testUser.getId());
        aiChatGuardService.resetLimitsForUser(otherUser.getId());
        aiChatGuardService.releaseSessionLock(testSession.getId());
    }

    @AfterEach
    void tearDown() {
        if (testSession != null) {
            aiChatGuardService.releaseSessionLock(testSession.getId());
        }
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Chu kỳ trọn vẹn: Tạo session -> Stream tin nhắn -> Lấy lịch sử -> Xóa session")
    void fullChatCycle_Success() throws Exception {
        // 1. Tạo session mới qua REST
        ChatSessionCreationRequest creationRequest = ChatSessionCreationRequest.builder()
                .title("Playlist Chill Cuối Tuần")
                .persona(AiPersona.GENZ_DJ)
                .build();

        ChatSession createdSession = ChatSession.builder()
                .id("session-created")
                .user(testUser)
                .title("Playlist Chill Cuối Tuần")
                .persona(AiPersona.GENZ_DJ)
                .build();
        createdSession.setCreatedAt(LocalDateTime.now());
        createdSession.setUpdatedAt(LocalDateTime.now());

        when(chatSessionRepository.save(any(ChatSession.class))).thenReturn(createdSession);
        when(chatSessionRepository.findById("session-created")).thenReturn(Optional.of(createdSession));

        mockMvc.perform(post("/ai/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(creationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("Playlist Chill Cuối Tuần"))
                .andExpect(jsonPath("$.result.persona").value("GENZ_DJ"));

        // 2. Mock Gemini Streaming thành công
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            @SuppressWarnings("unchecked")
            Consumer<GeminiClientService.StreamResult> callback = invocation.getArgument(3);

            emitter.send(SseEmitter.event().data("{\"delta\":\"Nhạc chill cho bạn đây\",\"done\":false}"));
            emitter.send(SseEmitter.event().data("{\"delta\":\"\",\"done\":true,\"status\":\"COMPLETE\",\"totalTokens\":50}"));
            emitter.complete();

            callback.accept(new GeminiClientService.StreamResult(
                    "Nhạc chill cho bạn đây",
                    50,
                    MessageStatus.COMPLETE,
                    null
            ));
            return null;
        }).when(geminiClientService).streamGenerateContent(any(), any(), any(), any());

        // 3. Gửi request stream SSE
        AiChatStreamRequest streamRequest = AiChatStreamRequest.builder()
                .sessionId("session-created")
                .message("Gợi ý cho tôi vài bài nhạc nhẹ nhàng")
                .build();

        mockMvc.perform(post("/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(streamRequest)))
                .andExpect(status().isOk());

        // 4. Kiểm tra lưu tin nhắn USER và AI
        verify(chatMessageRepository, atLeast(2)).save(any(ChatMessage.class));

        // 5. Lấy lịch sử tin nhắn qua REST API
        ChatMessage userMsg = ChatMessage.builder()
                .id("m1")
                .session(createdSession)
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content("Gợi ý cho tôi vài bài nhạc nhẹ nhàng")
                .build();
        ChatMessage aiMsg = ChatMessage.builder()
                .id("m2")
                .session(createdSession)
                .sender(MessageSender.AI)
                .status(MessageStatus.COMPLETE)
                .content("Nhạc chill cho bạn đây")
                .tokenCount(50)
                .build();

        when(chatMessageRepository.findBySessionOrderByCreatedAtAsc(createdSession))
                .thenReturn(List.of(userMsg, aiMsg));

        mockMvc.perform(get("/ai/chat/sessions/session-created/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").isArray())
                .andExpect(jsonPath("$.result.length()").value(2));

        // 6. Xóa session
        mockMvc.perform(delete("/ai/chat/sessions/session-created"))
                .andExpect(status().isOk());

        verify(chatSessionRepository, times(1)).delete(createdSession);

        // 7. Truy cập session không tồn tại -> 404 SESSION_NOT_EXISTED
        when(chatSessionRepository.findById("session-deleted")).thenReturn(Optional.empty());

        mockMvc.perform(get("/ai/chat/sessions/session-deleted/messages"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2008));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Concurrency Lock: Request thứ 2 vào cùng session đang stream nhận 409 Conflict")
    void streamChat_ConcurrencyLock_Returns409Conflict() throws Exception {
        boolean locked = aiChatGuardService.tryAcquireSessionLock(testSession.getId());
        assertThat(locked).isTrue();

        AiChatStreamRequest streamRequest = AiChatStreamRequest.builder()
                .sessionId(testSession.getId())
                .message("Request trùng lặp")
                .build();

        mockMvc.perform(post("/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(streamRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3002)); // AI_SESSION_BUSY
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Rate Limiting: Gửi quá 5 request/phút nhận 429 Too Many Requests")
    void streamChat_RateLimit_Returns429TooManyRequests() throws Exception {
        AiChatStreamRequest streamRequest = AiChatStreamRequest.builder()
                .sessionId(testSession.getId())
                .message("Tin nhắn test rate limit")
                .build();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<GeminiClientService.StreamResult> callback = invocation.getArgument(3);
            callback.accept(new GeminiClientService.StreamResult("OK", 5, MessageStatus.COMPLETE, null));
            return null;
        }).when(geminiClientService).streamGenerateContent(any(), any(), any(), any());

        // Gửi 5 request hợp lệ (giải phóng session lock sau mỗi request)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/ai/chat/stream")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(streamRequest)))
                    .andExpect(status().isOk());
            aiChatGuardService.releaseSessionLock(testSession.getId());
        }

        // Request thứ 6 bị chặn 429 Too Many Requests
        mockMvc.perform(post("/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(streamRequest)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(3001)); // AI_RATE_LIMIT_EXCEEDED
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("Ngắt kết nối giữa chừng: Tin nhắn USER không mất, câu trả lời dở lưu INTERRUPTED")
    void streamChat_ClientInterruption_PreservesPartialContent() throws Exception {
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<GeminiClientService.StreamResult> callback = invocation.getArgument(3);
            // Giả lập client ngắt kết nối khi AI mới trả lời được một đoạn
            callback.accept(new GeminiClientService.StreamResult(
                    "Đoạn văn bản đang nói dở thì bị rớt mạng...",
                    18,
                    MessageStatus.INTERRUPTED,
                    "Client disconnected"
            ));
            return null;
        }).when(geminiClientService).streamGenerateContent(any(), any(), any(), any());

        AiChatStreamRequest streamRequest = AiChatStreamRequest.builder()
                .sessionId(testSession.getId())
                .message("Câu hỏi cần giải đáp dài")
                .build();

        mockMvc.perform(post("/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(streamRequest)))
                .andExpect(status().isOk());

        // Kiểm tra trong DB qua ArgumentCaptor: tin nhắn USER được lưu, và AI message được lưu dạng INTERRUPTED
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, atLeast(2)).save(messageCaptor.capture());

        List<ChatMessage> captured = messageCaptor.getAllValues();
        ChatMessage userCaptured = captured.stream()
                .filter(m -> m.getSender() == MessageSender.USER)
                .findFirst().orElseThrow();
        ChatMessage aiCaptured = captured.stream()
                .filter(m -> m.getSender() == MessageSender.AI)
                .findFirst().orElseThrow();

        assertThat(userCaptured.getContent()).isEqualTo("Câu hỏi cần giải đáp dài");
        assertThat(userCaptured.getStatus()).isEqualTo(MessageStatus.COMPLETE);

        assertThat(aiCaptured.getContent()).isEqualTo("Đoạn văn bản đang nói dở thì bị rớt mạng...");
        assertThat(aiCaptured.getStatus()).isEqualTo(MessageStatus.INTERRUPTED);
        assertThat(aiCaptured.getTokenCount()).isEqualTo(18);

        // Khóa phiên phải được tự động giải phóng
        assertThat(aiChatGuardService.isSessionLocked(testSession.getId())).isFalse();
    }
}
