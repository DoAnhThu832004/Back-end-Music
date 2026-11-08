package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class AiChatRepositoryIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("test_identity_service")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private ChatSession testSession;

    @BeforeEach
    void setUp() {
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .username("test_tie_breaker_user")
                .password("encoded_pass")
                .firstName("Tie")
                .lastName("Breaker")
                .build());

        testSession = chatSessionRepository.save(ChatSession.builder()
                .user(testUser)
                .title("Test Dual Tie Breaker Session")
                .persona(AiPersona.GENZ_DJ)
                .build());
    }

    @Test
    @DisplayName("Kiểm chứng Dual Tie-Breaker: Khi cùng timestamp createdAt, USER ('U') luôn xếp trước AI ('A') trong forward replay")
    void testForwardReplay_TieBreaker_UserComesBeforeAi() {
        LocalDateTime sameTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        ChatMessage userMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.USER)
                .content("Xin chào bot!")
                .status(MessageStatus.COMPLETE)
                .build();
        userMsg.setCreatedAt(sameTimestamp);
        chatMessageRepository.save(userMsg);

        ChatMessage aiMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.AI)
                .content("Chào bạn, tôi là bot!")
                .status(MessageStatus.COMPLETE)
                .build();
        aiMsg.setCreatedAt(sameTimestamp);
        chatMessageRepository.save(aiMsg);

        List<ChatMessage> forwardList = chatMessageRepository.findBySessionOrderByCreatedAtAscSenderDesc(testSession);

        assertThat(forwardList).hasSize(2);
        assertThat(forwardList.get(0).getSender()).isEqualTo(MessageSender.USER);
        assertThat(forwardList.get(1).getSender()).isEqualTo(MessageSender.AI);
    }

    @Test
    @DisplayName("Kiểm chứng Dual Tie-Breaker: Khi cùng timestamp createdAt, AI xếp trước USER trong reverse history")
    void testReverseHistory_TieBreaker_AiComesBeforeUser() {
        LocalDateTime sameTimestamp = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

        ChatMessage userMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.USER)
                .content("Prompt của user")
                .status(MessageStatus.COMPLETE)
                .build();
        userMsg.setCreatedAt(sameTimestamp);
        chatMessageRepository.save(userMsg);

        ChatMessage aiMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.AI)
                .content("Phản hồi của AI")
                .status(MessageStatus.COMPLETE)
                .build();
        aiMsg.setCreatedAt(sameTimestamp);
        chatMessageRepository.save(aiMsg);

        List<ChatMessage> reverseList = chatMessageRepository.findRecentMessagesBySessionIdAndStatus(
                testSession.getId(),
                MessageStatus.COMPLETE,
                PageRequest.of(0, 10)
        );

        assertThat(reverseList).hasSize(2);
        assertThat(reverseList.get(0).getSender()).isEqualTo(MessageSender.AI);
        assertThat(reverseList.get(1).getSender()).isEqualTo(MessageSender.USER);
    }

    @Test
    @DisplayName("Kiểm chứng DATETIME(6): Bảo toàn chênh lệch microsecond (+5ms) giữa câu hỏi và phản hồi")
    void testMicrosecondTimestampPreservation() {
        LocalDateTime userTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        LocalDateTime aiTime = userTime.plusNanos(5_000_000);

        ChatMessage userMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.USER)
                .content("Câu hỏi 1")
                .status(MessageStatus.COMPLETE)
                .build();
        userMsg.setCreatedAt(userTime);
        ChatMessage savedUser = chatMessageRepository.save(userMsg);

        ChatMessage aiMsg = ChatMessage.builder()
                .session(testSession)
                .sender(MessageSender.AI)
                .content("Câu trả lời 1")
                .status(MessageStatus.COMPLETE)
                .build();
        aiMsg.setCreatedAt(aiTime);
        ChatMessage savedAi = chatMessageRepository.save(aiMsg);

        assertThat(savedUser.getCreatedAt()).isEqualTo(userTime);
        assertThat(savedAi.getCreatedAt()).isEqualTo(aiTime);
        assertThat(savedAi.getCreatedAt()).isAfter(savedUser.getCreatedAt());
    }
}
