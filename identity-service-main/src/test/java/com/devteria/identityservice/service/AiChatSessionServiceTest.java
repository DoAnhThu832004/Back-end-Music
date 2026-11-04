package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.request.ChatSessionCreationRequest;
import com.devteria.identityservice.dto.response.ChatMessageResponse;
import com.devteria.identityservice.dto.response.ChatSessionResponse;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private AiChatSessionService aiChatSessionService;

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
                .id("session-abc")
                .user(sampleUser)
                .title("Cuộc trò chuyện mới")
                .persona(AiPersona.GENZ_DJ)
                .build();
        sampleSession.setCreatedAt(LocalDateTime.now());
        sampleSession.setUpdatedAt(LocalDateTime.now());

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
    @DisplayName("Tạo phiên chat thành công với giá trị mặc định")
    void createSession_Success_DefaultValues() {
        mockAuthentication();
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId("new-session-id");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            return s;
        });

        ChatSessionResponse response = aiChatSessionService.createSession(null);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("new-session-id");
        assertThat(response.getTitle()).isEqualTo("Cuộc trò chuyện mới");
        assertThat(response.getPersona()).isEqualTo(AiPersona.GENZ_DJ);
        verify(chatSessionRepository, times(1)).save(any(ChatSession.class));
    }

    @Test
    @DisplayName("Tạo phiên chat thành công với persona và title tùy chỉnh")
    void createSession_Success_CustomValues() {
        mockAuthentication();
        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId("custom-session-id");
            s.setCreatedAt(LocalDateTime.now());
            s.setUpdatedAt(LocalDateTime.now());
            return s;
        });

        ChatSessionCreationRequest request = ChatSessionCreationRequest.builder()
                .title("Tâm sự đêm muộn")
                .persona(AiPersona.HEALING_FRIEND)
                .build();

        ChatSessionResponse response = aiChatSessionService.createSession(request);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Tâm sự đêm muộn");
        assertThat(response.getPersona()).isEqualTo(AiPersona.HEALING_FRIEND);
    }

    @Test
    @DisplayName("Ném lỗi UNAUTHENTICATED khi chưa đăng nhập")
    void createSession_Unauthenticated() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertThatThrownBy(() -> aiChatSessionService.createSession(null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Lấy danh sách sessions sắp xếp theo thời gian mới nhất")
    void getSessions_Success() {
        mockAuthentication();
        when(chatSessionRepository.findByUserOrderByUpdatedAtDesc(sampleUser))
                .thenReturn(List.of(sampleSession));

        List<ChatSessionResponse> responses = aiChatSessionService.getSessions();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo("session-abc");
        assertThat(responses.get(0).getTitle()).isEqualTo("Cuộc trò chuyện mới");
    }

    @Test
    @DisplayName("Lấy lịch sử messages của session thành công")
    void getMessages_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-abc")).thenReturn(Optional.of(sampleSession));

        ChatMessage message = ChatMessage.builder()
                .id("msg-1")
                .session(sampleSession)
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content("Gợi ý bài hát chill")
                .tokenCount(10)
                .build();
        message.setCreatedAt(LocalDateTime.now());

        when(chatMessageRepository.findBySessionOrderByCreatedAtAsc(sampleSession))
                .thenReturn(List.of(message));

        List<ChatMessageResponse> messages = aiChatSessionService.getMessages("session-abc");

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getContent()).isEqualTo("Gợi ý bài hát chill");
        assertThat(messages.get(0).getSender()).isEqualTo(MessageSender.USER);
    }

    @Test
    @DisplayName("Chặn IDOR khi người dùng cố truy cập messages của người khác")
    void getMessages_Forbidden_IDOR() {
        mockAuthentication();

        ChatSession othersSession = ChatSession.builder()
                .id("session-other")
                .user(otherUser)
                .title("Session khác")
                .persona(AiPersona.GENZ_DJ)
                .build();

        when(chatSessionRepository.findById("session-other")).thenReturn(Optional.of(othersSession));

        assertThatThrownBy(() -> aiChatSessionService.getMessages("session-other"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verify(chatMessageRepository, never()).findBySessionOrderByCreatedAtAsc(any());
    }

    @Test
    @DisplayName("Ném SESSION_NOT_EXISTED khi sessionId không tồn tại")
    void getMessages_NotFound() {
        mockAuthentication();
        when(chatSessionRepository.findById("non-existent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiChatSessionService.getMessages("non-existent"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SESSION_NOT_EXISTED);
    }

    @Test
    @DisplayName("Xóa session thành công")
    void deleteSession_Success() {
        mockAuthentication();
        when(chatSessionRepository.findById("session-abc")).thenReturn(Optional.of(sampleSession));

        aiChatSessionService.deleteSession("session-abc");

        verify(chatSessionRepository, times(1)).delete(sampleSession);
    }

    @Test
    @DisplayName("Chặn IDOR khi người dùng cố xóa session của người khác")
    void deleteSession_Forbidden_IDOR() {
        mockAuthentication();

        ChatSession othersSession = ChatSession.builder()
                .id("session-other")
                .user(otherUser)
                .title("Session khác")
                .persona(AiPersona.GENZ_DJ)
                .build();

        when(chatSessionRepository.findById("session-other")).thenReturn(Optional.of(othersSession));

        assertThatThrownBy(() -> aiChatSessionService.deleteSession("session-other"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);

        verify(chatSessionRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Lấy active session có sẵn nếu đã tồn tại")
    void getOrCreateActiveSession_ExistingActive() {
        mockAuthentication();
        when(chatSessionRepository.findFirstByUserOrderByUpdatedAtDesc(sampleUser))
                .thenReturn(Optional.of(sampleSession));

        ChatSessionResponse response = aiChatSessionService.getOrCreateActiveSession(AiPersona.GENZ_DJ);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("session-abc");
        verify(chatSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tự động tạo session mới khi chưa có active session")
    void getOrCreateActiveSession_CreateNew() {
        mockAuthentication();
        when(chatSessionRepository.findFirstByUserOrderByUpdatedAtDesc(sampleUser))
                .thenReturn(Optional.empty());

        when(chatSessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession s = invocation.getArgument(0);
            s.setId("new-auto-session");
            return s;
        });

        ChatSessionResponse response = aiChatSessionService.getOrCreateActiveSession(AiPersona.MUSIC_CRITIC);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("new-auto-session");
        assertThat(response.getPersona()).isEqualTo(AiPersona.MUSIC_CRITIC);
    }

    @Test
    @DisplayName("Tự động cập nhật tiêu đề session từ câu hỏi đầu tiên nếu còn là mặc định")
    void updateSessionTitleIfDefault_Success() {
        when(chatSessionRepository.findById("session-abc")).thenReturn(Optional.of(sampleSession));

        aiChatSessionService.updateSessionTitleIfDefault("session-abc", "Gợi ý cho tôi vài bài nhạc rap US UK thịnh hành");

        assertThat(sampleSession.getTitle()).isEqualTo("Gợi ý cho tôi vài bài nhạc rap US UK...");
        verify(chatSessionRepository, times(1)).save(sampleSession);
    }

    @Test
    @DisplayName("Không đổi tiêu đề nếu người dùng đã đặt tên khác mặc định")
    void updateSessionTitleIfDefault_AlreadyRenamed() {
        sampleSession.setTitle("Playlist Yêu Thích 2026");
        when(chatSessionRepository.findById("session-abc")).thenReturn(Optional.of(sampleSession));

        aiChatSessionService.updateSessionTitleIfDefault("session-abc", "Có bài nào mới không?");

        assertThat(sampleSession.getTitle()).isEqualTo("Playlist Yêu Thích 2026");
        verify(chatSessionRepository, never()).save(sampleSession);
    }
}
