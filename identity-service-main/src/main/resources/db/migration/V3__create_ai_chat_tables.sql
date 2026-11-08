-- V3: Tạo bảng quản lý phiên trò chuyện và tin nhắn cho AI Music Companion
CREATE TABLE IF NOT EXISTS chat_session (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    persona VARCHAR(32) NOT NULL DEFAULT 'GENZ_DJ',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_chat_session_user_updated (user_id, updated_at DESC)
);

CREATE TABLE IF NOT EXISTS chat_message (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    sender VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    content TEXT NOT NULL,
    current_song_id VARCHAR(36) NULL,
    token_count INT DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_song FOREIGN KEY (current_song_id) REFERENCES song(id) ON DELETE SET NULL,
    INDEX idx_chat_message_session_created (session_id, created_at ASC)
);
