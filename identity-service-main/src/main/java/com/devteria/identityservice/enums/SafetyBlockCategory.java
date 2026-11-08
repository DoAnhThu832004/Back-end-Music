package com.devteria.identityservice.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * Phân loại các lý do chặn an toàn và chính sách của Google Gemini API
 * phục vụ việc giám sát Counter ai.gemini.safety.blocks với Bounded Cardinality.
 *
 * Đối chiếu theo tài liệu chính thức Google Gemini API v1beta (tháng 11/2024):
 * - BlockReason (Prompt): https://ai.google.dev/api/rest/v1beta/GenerateContentResponse#blockreason
 * - FinishReason (Candidate): https://ai.google.dev/api/rest/v1beta/Candidate#finishreason
 */
public enum SafetyBlockCategory {
    SAFETY,
    RECITATION,
    POLICY,
    PRIVACY,
    OTHER;

    /**
     * Ánh xạ từ promptFeedback.blockReason của Google Gemini API.
     * Khi prompt bị Google chặn ngay từ đầu vào.
     */
    public static SafetyBlockCategory fromPromptBlockReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return OTHER;
        }
        return switch (rawReason.trim().toUpperCase(Locale.ROOT)) {
            case "SAFETY" -> SAFETY;
            case "BLOCKLIST", "PROHIBITED_CONTENT" -> POLICY;
            case "BLOCK_REASON_UNSPECIFIED", "OTHER" -> OTHER;
            default -> OTHER; // Nhánh mặc định bắt buộc: hấp thụ toàn bộ giá trị không xác định hoặc Google bổ sung sau này
        };
    }

    /**
     * Ánh xạ từ candidates[0].finishReason của Google Gemini API.
     * Trả về Optional rỗng nếu không phải là lý do chặn an toàn/chính sách
     * (ví dụ STOP, MAX_TOKENS, LANGUAGE, MALFORMED_FUNCTION_CALL, OTHER).
     */
    public static Optional<SafetyBlockCategory> fromCandidateFinishReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return Optional.empty();
        }
        return switch (rawReason.trim().toUpperCase(Locale.ROOT)) {
            case "SAFETY" -> Optional.of(SAFETY);
            case "RECITATION" -> Optional.of(RECITATION);
            case "BLOCKLIST", "PROHIBITED_CONTENT" -> Optional.of(POLICY);
            case "SPII" -> Optional.of(PRIVACY);
            // STOP, MAX_TOKENS, OTHER, LANGUAGE, MALFORMED_FUNCTION_CALL không được tính là safety block
            default -> Optional.empty();
        };
    }

    /**
     * Phương thức ánh xạ tổng quát tương thích ngược có fallback an toàn về OTHER.
     */
    public static SafetyBlockCategory fromGemini(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return OTHER;
        }
        return switch (rawReason.trim().toUpperCase(Locale.ROOT)) {
            case "SAFETY" -> SAFETY;
            case "RECITATION" -> RECITATION;
            case "BLOCKLIST", "PROHIBITED_CONTENT" -> POLICY;
            case "SPII" -> PRIVACY;
            default -> OTHER;
        };
    }
}
