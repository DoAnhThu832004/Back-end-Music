package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class ConversationMemoryManager {

    public static final int MAX_HISTORY_MESSAGES = 8;
    public static final int MAX_HISTORY_CHARS = 3000;

    private final ChatMessageRepository chatMessageRepository;

    public ConversationMemoryManager(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Lấy danh sách tin nhắn lịch sử hợp lệ với cơ chế Sliding Window:
     * - Tối đa 8 tin nhắn gần nhất có trạng thái COMPLETE.
     * - Tổng ngân sách ký tự lịch sử không vượt quá 3000 ký tự (duyệt từ mới nhất ngược về cũ).
     * - Kết quả được đảo ngược theo thứ tự thời gian tăng dần (cũ nhất -> mới nhất).
     */
    public List<ChatMessage> getSlidingWindowHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        List<ChatMessage> recentMessages = chatMessageRepository.findRecentMessagesBySessionIdAndStatus(
                sessionId,
                MessageStatus.COMPLETE,
                PageRequest.of(0, MAX_HISTORY_MESSAGES)
        );

        if (recentMessages == null || recentMessages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatMessage> selectedMessages = new ArrayList<>();
        int currentTotalChars = 0;

        for (ChatMessage message : recentMessages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            int msgLength = message.getContent().length();
            if (currentTotalChars + msgLength > MAX_HISTORY_CHARS && !selectedMessages.isEmpty()) {
                log.info("[Memory Manager] Ngân sách ký tự lịch sử đạt ngưỡng ({}/{} ký tự), dừng cắt sliding window.",
                        currentTotalChars, MAX_HISTORY_CHARS);
                break;
            }
            selectedMessages.add(message);
            currentTotalChars += msgLength;
        }

        // Đảo ngược lại để có thứ tự thời gian từ cũ nhất -> mới nhất
        Collections.reverse(selectedMessages);
        return selectedMessages;
    }

    /**
     * Xây dựng danh sách `contents` chuẩn cho Gemini Multi-turn API:
     * - USER -> role: "user"
     * - AI -> role: "model"
     * - Đảm bảo xen kẽ user/model nghiêm ngặt để Gemini không trả về 400 Bad Request
     * - Thêm câu hỏi an toàn hiện tại của user vào cuối cùng
     */
    public List<Map<String, Object>> buildGeminiContents(List<ChatMessage> history, String currentSafeUserPrompt) {
        List<Map<String, Object>> contents = new ArrayList<>();

        String lastRole = null;
        StringBuilder currentRoleText = new StringBuilder();

        if (history != null) {
            for (ChatMessage message : history) {
                String role = (message.getSender() == MessageSender.AI) ? "model" : "user";
                String text = message.getContent() != null ? message.getContent() : "";

                if (role.equals(lastRole)) {
                    // Nếu cùng role liên tiếp, gộp text để bảo đảm strict alternation
                    currentRoleText.append("\n").append(text);
                } else {
                    if (lastRole != null) {
                        contents.add(createContentBlock(lastRole, currentRoleText.toString()));
                    }
                    lastRole = role;
                    currentRoleText = new StringBuilder(text);
                }
            }

            if (lastRole != null) {
                contents.add(createContentBlock(lastRole, currentRoleText.toString()));
            }
        }

        // Thêm tin nhắn hiện tại của User
        String userTurnText = (currentSafeUserPrompt != null) ? currentSafeUserPrompt : "";
        if (!contents.isEmpty() && "user".equals(lastRole)) {
            int lastIdx = contents.size() - 1;
            Map<String, Object> lastBlock = contents.get(lastIdx);
            @SuppressWarnings("unchecked")
            List<Map<String, String>> parts = (List<Map<String, String>>) lastBlock.get("parts");
            String combined = parts.get(0).get("text") + "\n" + userTurnText;
            contents.set(lastIdx, createContentBlock("user", combined));
        } else {
            contents.add(createContentBlock("user", userTurnText));
        }

        return contents;
    }

    private Map<String, Object> createContentBlock(String role, String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("role", role);
        block.put("parts", List.of(Map.of("text", text)));
        return block;
    }
}
