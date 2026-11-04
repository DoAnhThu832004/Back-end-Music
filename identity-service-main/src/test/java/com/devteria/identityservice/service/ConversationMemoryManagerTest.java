package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import com.devteria.identityservice.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryManagerTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ConversationMemoryManager memoryManager;

    private ChatMessage msg1; // cũ hơn
    private ChatMessage msg2; // mới hơn

    @BeforeEach
    void setUp() {
        msg1 = ChatMessage.builder()
                .id("msg-1")
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content("Chào AI!")
                .build();
        msg1.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        msg2 = ChatMessage.builder()
                .id("msg-2")
                .sender(MessageSender.AI)
                .status(MessageStatus.COMPLETE)
                .content("Chào bạn, mình có thể gợi ý nhạc gì cho bạn hôm nay?")
                .build();
        msg2.setCreatedAt(LocalDateTime.now().minusMinutes(4));
    }

    @Test
    @DisplayName("Lấy lịch sử trả về thứ tự thời gian tăng dần (cũ nhất -> mới nhất)")
    void getSlidingWindowHistory_ReturnsChronologicalOrder() {
        // Repository trả về thứ tự DESC (mới nhất trước: msg2 rồi msg1)
        when(chatMessageRepository.findRecentMessagesBySessionIdAndStatus(eq("session-1"), eq(MessageStatus.COMPLETE), any(Pageable.class)))
                .thenReturn(List.of(msg2, msg1));

        List<ChatMessage> result = memoryManager.getSlidingWindowHistory("session-1");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("msg-1");
        assertThat(result.get(1).getId()).isEqualTo("msg-2");
    }

    @Test
    @DisplayName("Trả về danh sách rỗng khi sessionId null hoặc không có tin nhắn")
    void getSlidingWindowHistory_HandlesEmptyOrNull() {
        assertThat(memoryManager.getSlidingWindowHistory(null)).isEmpty();
        assertThat(memoryManager.getSlidingWindowHistory("")).isEmpty();

        when(chatMessageRepository.findRecentMessagesBySessionIdAndStatus(eq("session-empty"), eq(MessageStatus.COMPLETE), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        assertThat(memoryManager.getSlidingWindowHistory("session-empty")).isEmpty();
    }

    @Test
    @DisplayName("Cắt sliding window khi tổng ký tự vượt quá ngân sách 3000 ký tự")
    void getSlidingWindowHistory_RespectsMax3000CharsBudget() {
        // Tạo tin nhắn mới 2000 ký tự và tin nhắn cũ 2000 ký tự (tổng 4000 > 3000)
        String longText = "A".repeat(2000);

        ChatMessage recentMsg = ChatMessage.builder()
                .id("recent-msg")
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content(longText)
                .build();

        ChatMessage olderMsg = ChatMessage.builder()
                .id("older-msg")
                .sender(MessageSender.AI)
                .status(MessageStatus.COMPLETE)
                .content(longText)
                .build();

        // Repository trả về recentMsg trước, olderMsg sau
        when(chatMessageRepository.findRecentMessagesBySessionIdAndStatus(eq("session-1"), eq(MessageStatus.COMPLETE), any(Pageable.class)))
                .thenReturn(List.of(recentMsg, olderMsg));

        List<ChatMessage> result = memoryManager.getSlidingWindowHistory("session-1");

        // Chỉ giữ recentMsg vì nếu thêm olderMsg sẽ thành 4000 ký tự (> 3000)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("recent-msg");
    }

    @Test
    @DisplayName("buildGeminiContents chuẩn hóa role user/model và đính kèm câu hỏi hiện tại")
    @SuppressWarnings("unchecked")
    void buildGeminiContents_FormatsRolesCorrectly() {
        List<ChatMessage> history = List.of(msg1, msg2);
        String currentPrompt = "<user_content>Gợi ý bài hát buồn</user_content>";

        List<Map<String, Object>> contents = memoryManager.buildGeminiContents(history, currentPrompt);

        assertThat(contents).hasSize(3);

        // Turn 1: User
        assertThat(contents.get(0).get("role")).isEqualTo("user");
        List<Map<String, String>> parts1 = (List<Map<String, String>>) contents.get(0).get("parts");
        assertThat(parts1.get(0).get("text")).isEqualTo("Chào AI!");

        // Turn 2: Model
        assertThat(contents.get(1).get("role")).isEqualTo("model");
        List<Map<String, String>> parts2 = (List<Map<String, String>>) contents.get(1).get("parts");
        assertThat(parts2.get(0).get("text")).contains("gợi ý nhạc");

        // Turn 3: Current User Prompt
        assertThat(contents.get(2).get("role")).isEqualTo("user");
        List<Map<String, String>> parts3 = (List<Map<String, String>>) contents.get(2).get("parts");
        assertThat(parts3.get(0).get("text")).isEqualTo(currentPrompt);
    }

    @Test
    @DisplayName("buildGeminiContents gộp tin nhắn cùng role liên tiếp để bảo đảm xen kẽ nghiêm ngặt")
    @SuppressWarnings("unchecked")
    void buildGeminiContents_EnforcesStrictAlternation() {
        ChatMessage userMsg1 = ChatMessage.builder()
                .id("u1")
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content("Ý 1")
                .build();

        ChatMessage userMsg2 = ChatMessage.builder()
                .id("u2")
                .sender(MessageSender.USER)
                .status(MessageStatus.COMPLETE)
                .content("Ý 2")
                .build();

        // 2 tin nhắn USER liên tiếp
        List<ChatMessage> history = List.of(userMsg1, userMsg2);
        String currentPrompt = "Ý 3";

        List<Map<String, Object>> contents = memoryManager.buildGeminiContents(history, currentPrompt);

        // Vì toàn bộ là turn user, được gộp thành 1 turn user duy nhất
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).get("role")).isEqualTo("user");
        List<Map<String, String>> parts = (List<Map<String, String>>) contents.get(0).get("parts");
        assertThat(parts.get(0).get("text")).contains("Ý 1").contains("Ý 2").contains("Ý 3");
    }
}
