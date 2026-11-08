package com.devteria.identityservice.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * Phân loại các lý do chặn an toàn và chính sách của Google Gemini API
 * phục vụ việc giám sát Counter ai.gemini.safety.blocks với Bounded Cardinality.
 *
 * Đối chiếu theo đặc tả Google Gemini API (v1beta & gemini-3.5 — cần xác nhận lại khi chạy thực tế với upstream):
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
     * Trả về Optional rỗng nếu không có blockReason (tức prompt không bị chặn đầu vào).
     * Khi có chuỗi blockReason, ánh xạ về SAFETY, POLICY, hoặc OTHER (default).
     *
     * <p><b>Giả định tiền điều kiện:</b> Phương thức chỉ nhận dữ liệu khi {@code promptFeedback != null && promptFeedback.getBlockReason() != null}
     * (tức upstream đã thực sự kích hoạt cơ chế chặn đầu vào). Do đó, nhánh {@code default -> Optional.of(OTHER)} đóng vai trò
     * là chốt chặn an toàn hấp thụ mọi lý do chặn mới trong tương lai mà không làm nổ cardinality, không phải để nhận chuỗi tùy ý.</p>
     */
    public static Optional<SafetyBlockCategory> fromPromptBlockReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return Optional.empty();
        }
        return switch (rawReason.trim().toUpperCase(Locale.ROOT)) {
            case "SAFETY" -> Optional.of(SAFETY);
            case "BLOCKLIST", "PROHIBITED_CONTENT" -> Optional.of(POLICY);
            default -> Optional.of(OTHER); // Hấp thụ an toàn BLOCK_REASON_UNSPECIFIED, OTHER và các lý do mới của Google
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
     * @deprecated Phương thức này không phân biệt giữa Prompt Block và Candidate FinishReason,
     * có rủi ro ánh xạ nhầm các trạng thái bình thường (như STOP, MAX_TOKENS) thành OTHER và làm phình metric an toàn.
     * Đã rà soát codebase: không có mã nguồn nghiệp vụ nào gọi hàm này; duy nhất testFromGemini_Deprecated()
     * trong SafetyBlockCategoryTest kiểm chứng tương thích ngược với @SuppressWarnings("deprecation").
     * Khuyến nghị dùng trực tiếp {@link #fromPromptBlockReason(String)} hoặc {@link #fromCandidateFinishReason(String)}.
     */
    @Deprecated(since = "Layer-7-Rev20", forRemoval = true)
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
