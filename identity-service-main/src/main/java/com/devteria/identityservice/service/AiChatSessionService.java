package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.request.ChatSessionCreationRequest;
import com.devteria.identityservice.dto.response.ChatMessageResponse;
import com.devteria.identityservice.dto.response.ChatSessionResponse;
import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.ChatMessageRepository;
import com.devteria.identityservice.repository.ChatSessionRepository;
import com.devteria.identityservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class AiChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    public AiChatSessionService(ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                UserRepository userRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    @Transactional
    public ChatSessionResponse createSession(ChatSessionCreationRequest request) {
        User user = getAuthenticatedUser();
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Cuộc trò chuyện mới";

        AiPersona persona = (request != null && request.getPersona() != null)
                ? request.getPersona()
                : AiPersona.GENZ_DJ;

        ChatSession session = ChatSession.builder()
                .user(user)
                .title(title)
                .persona(persona)
                .build();

        ChatSession saved = chatSessionRepository.save(session);
        log.info("[AI Chat] Đã tạo phiên chat mới: id={}, user={}, persona={}", saved.getId(), user.getUsername(), persona);
        return toSessionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> getSessions() {
        User user = getAuthenticatedUser();
        return chatSessionRepository.findByUserOrderByUpdatedAtDesc(user)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(String sessionId) {
        User user = getAuthenticatedUser();
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_EXISTED));

        // Kiểm tra quyền sở hữu IDOR
        if (!session.getUser().getId().equals(user.getId())) {
            log.warn("[AI Chat] Người dùng {} cố tình truy cập trái phép session {} của người khác (IDOR bị chặn)", user.getUsername(), sessionId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return chatMessageRepository.findBySessionOrderByCreatedAtAsc(session)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void deleteSession(String sessionId) {
        User user = getAuthenticatedUser();
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new AppException(ErrorCode.SESSION_NOT_EXISTED));

        // Kiểm tra quyền sở hữu IDOR
        if (!session.getUser().getId().equals(user.getId())) {
            log.warn("[AI Chat] Người dùng {} cố tình xóa trái phép session {} của người khác (IDOR bị chặn)", user.getUsername(), sessionId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        chatSessionRepository.delete(session);
        log.info("[AI Chat] Người dùng {} đã xóa phiên chat {}", user.getUsername(), sessionId);
    }

    @Transactional
    public ChatSessionResponse getOrCreateActiveSession(AiPersona persona) {
        User user = getAuthenticatedUser();
        Optional<ChatSession> activeOpt = chatSessionRepository.findFirstByUserOrderByUpdatedAtDesc(user);
        if (activeOpt.isPresent()) {
            return toSessionResponse(activeOpt.get());
        }
        return createSession(ChatSessionCreationRequest.builder()
                .persona(persona != null ? persona : AiPersona.GENZ_DJ)
                .title("Cuộc trò chuyện mới")
                .build());
    }

    @Transactional
    public void updateSessionTitleIfDefault(String sessionId, String firstQuestion) {
        if (firstQuestion == null || firstQuestion.isBlank()) return;
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            if ("Cuộc trò chuyện mới".equals(session.getTitle())) {
                String cleanTitle = firstQuestion.trim().replaceAll("\\s+", " ");
                if (cleanTitle.length() > 40) {
                    cleanTitle = cleanTitle.substring(0, 37).trim() + "...";
                }
                session.setTitle(cleanTitle);
                chatSessionRepository.save(session);
                log.info("[AI Chat] Đã tự động cập nhật tiêu đề session {}: {}", sessionId, cleanTitle);
            }
        });
    }

    public ChatSessionResponse toSessionResponse(ChatSession session) {
        return ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .persona(session.getPersona())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }

    public ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .sessionId(message.getSession().getId())
                .sender(message.getSender())
                .status(message.getStatus())
                .content(message.getContent())
                .currentSongId(message.getCurrentSong() != null ? message.getCurrentSong().getId() : null)
                .currentSongName(message.getCurrentSong() != null ? message.getCurrentSong().getName() : null)
                .tokenCount(message.getTokenCount())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
