package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.CacheBypassReason;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Đánh giá khả năng lưu và tra cứu Cache (Can-Cache Evaluator - Lớp 7).
 * - Quét 2 tầng Có dấu (Accented) và Không dấu (Unaccented).
 * - Sử dụng các hằng số Pattern tĩnh biên dịch trước (tránh cấp phát lại).
 * - Phân loại 8 lý do BYPASS để bảo vệ an toàn dữ liệu người dùng và ngữ cảnh hội thoại.
 */
@Component
public class CanCacheEvaluator {

    private static final Pattern COMBINING_DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");

    // 1. Dữ liệu cá nhân (Personal Data) - Quét có dấu (UNICODE (?U)) và không dấu
    // Lưu ý: Bắt buộc có "của" cho đại từ để tránh bắt nhầm "nhạc Anh" (nhạc nước Anh/tiếng Anh) thành personal data
    private static final Pattern PERSONAL_ACCENTED = Pattern.compile(
            "(?U)\\b(?:của (?:tôi|mình|em|anh)|gu (?:của )?(?:tôi|mình|em|anh)|playlist (?:của )?(?:tôi|mình|em|anh)|danh sách (?:của )?(?:tôi|mình|em|anh)|nhạc của (?:tôi|mình|em|anh)|bài (?:hát )?(?:của )?(?:tôi|mình) (?:yêu )?thích|(?:tôi|mình) (?:hay nghe|yêu thích|thích|đã nghe)|lịch sử nghe)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PERSONAL_UNACCENTED = Pattern.compile(
            "\\b(?:cua (?:toi|minh|em|anh)|gu (?:cua )?(?:toi|minh|em|anh)|playlist (?:cua )?(?:toi|minh|em|anh)|danh sach (?:cua )?(?:toi|minh|em|anh)|nhac cua (?:toi|minh|em|anh)|bai (?:hat )?(?:cua )?(?:toi|minh) (?:yeu )?thich|(?:toi|minh) (?:hay nghe|yeu thich|thich|da nghe)|lich su nghe)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 2. Thông tin nhận dạng cá nhân (PII) - Số điện thoại, Email, CCCD/CMND
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:0|\\+84)(?:[\\s.-]?[0-9]){9,10}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b\\d{9,12}\\b");

    // 3. Đại từ thay thế phụ thuộc ngữ cảnh (Anaphora)
    private static final Pattern ANAPHORA_ACCENTED = Pattern.compile(
            "(?U)\\b(?:bài này|bài đó|bài vừa rồi|bài trước|ca khúc này|ca khúc đó)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ANAPHORA_UNACCENTED = Pattern.compile(
            "\\b(?:bai nay|bai do|bai vua roi|bai truoc|ca khuc nay|ca khuc do)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 4. Từ khóa thời gian thực (Temporal)
    private static final Pattern TEMPORAL_ACCENTED = Pattern.compile(
            "(?U)\\b(?:hôm nay|bây giờ|hiện tại|mới nhất|sáng nay|tối nay|tuần này|tháng này|năm nay)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TEMPORAL_UNACCENTED = Pattern.compile(
            "\\b(?:hom nay|bay gio|hien tai|moi nhat|sang nay|toi nay|tuan nay|thang nay|nam nay)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // 5. Quy chiếu lửng lơ (Dangling References)
    private static final Pattern DANGLING_ACCENTED = Pattern.compile(
            "(?U)\\b(?:còn bài nào|bài tiếp theo|tiếp đi|nữa không|còn không)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DANGLING_UNACCENTED = Pattern.compile(
            "\\b(?:con bai nao|bai tiep theo|tiep di|nua khong|con khong)\\b",
            Pattern.CASE_INSENSITIVE
    );

    public CacheEvaluationResult evaluate(String rawPrompt, String currentSongId, boolean hasPriorHistory) {
        // CỔNG 1: Kiểm tra câu hỏi rỗng
        if (rawPrompt == null || rawPrompt.trim().length() < 2) {
            return CacheEvaluationResult.bypass(CacheBypassReason.EMPTY_PROMPT);
        }

        // CỔNG 2: Kiểm tra lịch sử hội thoại (Chỉ cho phép Turn 1)
        if (hasPriorHistory) {
            return CacheEvaluationResult.bypass(CacheBypassReason.HISTORY);
        }

        // CỔNG 3: Kiểm tra ngữ cảnh bài hát đang phát
        if (currentSongId != null && !currentSongId.isBlank()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.HAS_SONG_CONTEXT);
        }

        // Chuẩn hóa chuỗi có dấu (NFC) và không dấu
        String nfc = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String singleSpaced = nfc.replaceAll("\\s+", " ");
        String lowerAccented = TRAILING_PUNCTUATION.matcher(singleSpaced.toLowerCase(Locale.ROOT)).replaceAll("").trim();
        String lowerUnaccented = stripDiacritics(lowerAccented);

        // CỔNG 4: Kiểm tra thông tin nhạy cảm PII
        if (PHONE_PATTERN.matcher(lowerAccented).find()
                || EMAIL_PATTERN.matcher(lowerAccented).find()
                || ID_CARD_PATTERN.matcher(lowerAccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.PII);
        }

        // CỔNG 5: Kiểm tra dữ liệu cá nhân (Personal Preferences)
        if (PERSONAL_ACCENTED.matcher(lowerAccented).find()
                || PERSONAL_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.PERSONAL);
        }

        // CỔNG 6: Kiểm tra đại từ thay thế (Anaphora)
        if (ANAPHORA_ACCENTED.matcher(lowerAccented).find()
                || ANAPHORA_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.ANAPHORA);
        }

        // CỔNG 7: Kiểm tra thời gian thực (Temporal)
        if (TEMPORAL_ACCENTED.matcher(lowerAccented).find()
                || TEMPORAL_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.TEMPORAL);
        }

        // CỔNG 8: Kiểm tra quy chiếu lửng lơ (Dangling Reference)
        if (DANGLING_ACCENTED.matcher(lowerAccented).find()
                || DANGLING_UNACCENTED.matcher(lowerUnaccented).find()) {
            return CacheEvaluationResult.bypass(CacheBypassReason.DANGLING_REF);
        }

        // Đủ điều kiện GENERAL
        return CacheEvaluationResult.general();
    }

    public static String stripDiacritics(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_DIACRITICAL_MARKS.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }
}
