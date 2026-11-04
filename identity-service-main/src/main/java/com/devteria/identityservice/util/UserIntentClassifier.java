package com.devteria.identityservice.util;

import java.util.List;

/**
 * Tiện ích phân loại ý định phát nhạc trực tiếp của người dùng (Task 3A.9).
 * Chỉ tự động kích hoạt autoExecute = true trong PlayerActionDto khi câu lệnh chứa động từ điều khiển trực tiếp.
 */
public final class UserIntentClassifier {

    private static final List<String> DIRECT_PLAY_VERBS = List.of(
            "bat", "phat", "mo", "play", "nghe bai", "choi bai", "bat bai", "phat bai", "mo bai"
    );

    private UserIntentClassifier() {
        // Utility
    }

    /**
     * Kiểm tra xem câu lệnh của người dùng có chứa ý định ra lệnh phát nhạc trực tiếp hay không.
     *
     * @param prompt Câu hỏi hoặc câu lệnh thô của người dùng
     * @return true nếu có ý định phát nhạc trực tiếp
     */
    public static boolean hasDirectPlaybackIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }

        String norm = VietnameseTextNormalizer.normalize(prompt);
        for (String verb : DIRECT_PLAY_VERBS) {
            if (norm.contains(verb)) {
                return true;
            }
        }
        return false;
    }
}
