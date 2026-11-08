package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.ChatMessage;
import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.enums.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    boolean existsBySessionId(String sessionId);

    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    List<ChatMessage> findBySessionOrderByCreatedAtAscSenderDesc(ChatSession session);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessagesBySessionId(@Param("sessionId") String sessionId);

    List<ChatMessage> findTop10BySessionAndStatusOrderByCreatedAtDesc(ChatSession session, MessageStatus status);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.status = :status ORDER BY m.createdAt DESC, m.sender ASC")
    List<ChatMessage> findRecentMessagesBySessionIdAndStatus(
            @Param("sessionId") String sessionId,
            @Param("status") MessageStatus status,
            Pageable pageable);
}
