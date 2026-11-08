package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.FastPathResult;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.util.VietnameseTextNormalizer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class FastPathEngine {

    public static final class ActionType {
        public static final String PAUSE = "PAUSE";
        public static final String RESUME = "RESUME";
        public static final String NEXT = "NEXT";
        public static final String PLAY = "PLAY";
        public static final String QUEUE = "QUEUE";
    }

    // Regex có dấu (Tier 1) - Chỉ nhận khi currentSongId != null
    private static final Pattern STOP_ACCENTED = Pattern.compile(
            "^(?:tạm dừng|dừng|tắt|dừng lại|ngừng phát)(?: nhạc)?(?: (?:đi|nhé|giúp|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_ACCENTED = Pattern.compile(
            "^(?:tiếp tục|phát tiếp)(?: nhạc)?(?: (?:đi|nhé|giúp|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_ACCENTED = Pattern.compile(
            "^(?:bài tiếp|qua bài|chuyển bài|next bài|bài sau|bài kế tiếp|bài tiếp theo)(?: (?:đi|nhé|ạ|với|mình|cho mình|giùm|hộ|nào|nha))*$",
            Pattern.CASE_INSENSITIVE);

    // Regex không dấu (Tier 2) - Phân nhóm dứt khoát, hỗ trợ đầy đủ 'tam dung nhac', 'tat', 'tat nhac'
    private static final Pattern STOP_UNACCENTED = Pattern.compile(
            "^(?:tam dung(?: nhac)?|tat(?: nhac)?|dung lai|ngung phat|stop|pause)(?: (?:di|nhe|giup|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESUME_UNACCENTED = Pattern.compile(
            "^(?:tiep tuc(?: nhac)?|phat tiep(?: nhac)?|resume)(?: (?:di|nhe|giup|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_UNACCENTED = Pattern.compile(
            "^(?:bai tiep|next|qua bai|chuyen bai|next bai|bai sau|bai ke tiep|bai tiep theo)(?: (?:di|nhe|a|voi|minh|cho minh|dum|ho|nao|nha))*$",
            Pattern.CASE_INSENSITIVE);

    // Khớp chào hỏi và danh tính
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^(?:chào|chao|hi|hello|xin chào|xin chao|helo|chào bạn|chao ban|chào bot|chao bot|hai bot|hai ban)(?: (?:nhé|nhe|ạ|a|nha))*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTITY_PATTERN = Pattern.compile(
            "^(?:bạn là ai|ban la ai|bạn tên gì|ban ten gi|tên bạn là gì|ten ban la gi|giới thiệu về bạn|gioi thieu ve ban)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");
    private static final Pattern VIETNAMESE_DIACRITICS = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ]",
            Pattern.CASE_INSENSITIVE);

    private FastPathEngine() {}

    public static Optional<FastPathResult> evaluate(
            String rawPrompt,
            AiPersona persona,
            String currentSongId,
            boolean hasPriorHistory,
            boolean clientSupportsTransportActions) {

        if (rawPrompt == null || rawPrompt.isBlank()) {
            return Optional.empty();
        }

        // 1. Chuẩn hóa NFC, gộp khoảng trắng và cắt dấu câu cuối
        String nfcPrompt = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String singleSpaced = nfcPrompt.replaceAll("\\s+", " ");
        String lowerRaw = TRAILING_PUNCTUATION.matcher(singleSpaced.toLowerCase(Locale.ROOT)).replaceAll("").trim();

        // 2. Chặn triệt để các câu phủ định (ở đầu, giữa hoặc đứng độc lập) - Phòng thủ chiều sâu
        if (lowerRaw.startsWith("đừng ") || lowerRaw.startsWith("không ") || lowerRaw.startsWith("chớ ")
                || lowerRaw.contains(" đừng ") || lowerRaw.contains(" không ") || lowerRaw.contains(" chớ ")
                || lowerRaw.equals("đừng") || lowerRaw.equals("không") || lowerRaw.equals("chớ")) {
            return Optional.empty();
        }

        // 3. Giới hạn độ dài an toàn và chặn định danh bài hát/ca sĩ
        if (lowerRaw.length() > 60 || lowerRaw.contains(" ca khúc ") || lowerRaw.contains(" ca sĩ ") || lowerRaw.contains(" của ")) {
            return Optional.empty();
        }

        // 4. Khớp câu chào hỏi / danh tính (Chỉ áp dụng khi hasPriorHistory == false)
        if (!hasPriorHistory) {
            if (IDENTITY_PATTERN.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.textMessage(resolveIdentityResponse(persona)));
            }
            if (GREETING_PATTERN.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.textMessage(resolveGreetingResponse(persona)));
            }
        }

        // 5. Khớp lệnh Player Control:
        // ĐIỀU KIỆN 1: Phải có bài đang phát (currentSongId != null)
        // ĐIỀU KIỆN 2: Client phải hỗ trợ Transport Actions (clientSupportsTransportActions == true)
        if (currentSongId == null || currentSongId.isBlank() || !clientSupportsTransportActions) {
            return Optional.empty();
        }

        boolean hasDiacritics = VIETNAMESE_DIACRITICS.matcher(lowerRaw).find();

        if (hasDiacritics) {
            if (STOP_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.PAUSE, "Đã tạm dừng phát nhạc."));
            }
            if (RESUME_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.RESUME, "Đã tiếp tục phát nhạc."));
            }
            if (NEXT_ACCENTED.matcher(lowerRaw).matches()) {
                return Optional.of(FastPathResult.playerAction(ActionType.NEXT, "Đã gửi lệnh chuyển bài."));
            }
        }

        // Tier 2: Khớp không dấu (áp dụng cho câu không dấu hoặc câu gõ nửa nạc nửa mỡ không khớp Tier 1)
        String unaccented = hasDiacritics ? VietnameseTextNormalizer.stripDiacritics(lowerRaw) : lowerRaw;
        if (STOP_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.PAUSE, "Đã tạm dừng phát nhạc."));
        }
        if (RESUME_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.RESUME, "Đã tiếp tục phát nhạc."));
        }
        if (NEXT_UNACCENTED.matcher(unaccented).matches()) {
            return Optional.of(FastPathResult.playerAction(ActionType.NEXT, "Đã gửi lệnh chuyển bài."));
        }

        return Optional.empty();
    }

    private static String resolveIdentityResponse(AiPersona persona) {
        return switch (persona != null ? persona : AiPersona.GENZ_DJ) {
            case GENZ_DJ -> "Mình là GenZ DJ - trợ lý âm nhạc AI siêu nhiệt huyết của bạn! Mình có thể bật nhạc, mix playlist và trò chuyện cực cuốn.";
            case MUSIC_CRITIC -> "Tôi là Chuyên gia Phê bình Âm nhạc (Music Critic). Tôi đồng hành cùng quý vị để phân tích cấu trúc, hòa âm và giá trị nghệ thuật tác phẩm.";
            case HEALING_FRIEND -> "Mình là Người bạn Chữa lành (Healing Friend). Mình ở đây để lắng nghe tâm sự và xoa dịu tâm hồn bạn bằng những giai điệu bình yên.";
        };
    }

    private static String resolveGreetingResponse(AiPersona persona) {
        return switch (persona != null ? persona : AiPersona.GENZ_DJ) {
            case GENZ_DJ -> "Yo! GenZ DJ đây, hôm nay bạn muốn lên dây cót tinh thần bằng giai điệu nào? Bật mí ngay nhé!";
            case MUSIC_CRITIC -> "Kính chào quý thính giả. Hôm nay chúng ta cùng khám phá và thưởng thức tác phẩm âm nhạc nào?";
            case HEALING_FRIEND -> "Chào bạn, mình luôn ở đây để lắng nghe bạn. Hôm nay tâm trạng của bạn cảm thấy thế nào?";
        };
    }
}
