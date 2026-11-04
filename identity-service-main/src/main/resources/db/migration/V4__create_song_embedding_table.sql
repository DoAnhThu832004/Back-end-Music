-- V4: Bảng lưu trữ Vector Embedding của bài hát (phục vụ In-Memory Cosine Similarity và Hybrid Search)
CREATE TABLE IF NOT EXISTS song_embedding (
    song_id VARCHAR(36) PRIMARY KEY,
    search_text TEXT NOT NULL,              -- Văn bản tổng hợp: Tên + Nghệ sĩ + Thể loại + Moods + Mô tả
    normalized_title VARCHAR(255) NOT NULL, -- Tên bài hát tiếng Việt không dấu (phục vụ tìm kiếm nhanh)
    embedding_json TEXT NOT NULL,           -- Mảng float vector từ model Gemini embedding
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_song_embedding_song FOREIGN KEY (song_id) REFERENCES song(id) ON DELETE CASCADE,
    INDEX idx_song_normalized_title (normalized_title)
);
