package com.devteria.identityservice.repository;

import com.devteria.identityservice.entity.ChatSession;
import com.devteria.identityservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByUserOrderByUpdatedAtDesc(User user);
    Optional<ChatSession> findFirstByUserOrderByUpdatedAtDesc(User user);
    boolean existsByIdAndUser(String id, User user);
}
