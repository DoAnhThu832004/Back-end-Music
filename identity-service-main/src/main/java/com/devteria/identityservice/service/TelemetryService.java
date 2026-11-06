package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Dịch vụ thu thập Telemetry cho Lớp 7 (Cache & Model Router).
 * - Theo dõi tỉ lệ câu hỏi lặp lại trong cửa sổ trượt 30 phút (Caffeine 50.000 mục).
 * - Phân biệt chính xác giữa lặp lại liên người dùng (exact_repeat_hits) và người dùng tự thử lại (self_repeat_hits).
 * - Chuẩn hóa Canonical Prompt (NFC, bỏ dấu, cắt dấu câu cuối, gộp khoảng trắng thừa, SHA-256).
 * - Hoàn toàn fail-safe: Không bao giờ làm gián đoạn luồng chat của người dùng.
 */
@Service
@Slf4j
public class TelemetryService {

    private static final Pattern COMBINING_DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private final MeterRegistry meterRegistry;
    // Lưu key: sha256CanonicalPrompt, value: userIdOrSessionId
    private final Cache<String, String> repeat30mCache;

    public TelemetryService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.repeat30mCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Ghi nhận Telemetry cho một lượt tương tác của người dùng.
     * Hoàn toàn bọc trong khối try-catch để đảm bảo tính an toàn (fail-safe).
     */
    public void recordPromptTelemetry(String userId, String rawPrompt, CacheEvaluationResult evalResult) {
        try {
            if (meterRegistry == null || evalResult == null) {
                return;
            }

            if (evalResult.scope() == CacheScope.BYPASS) {
                meterRegistry.counter("ai.cache.semantic.eligibility", "eligible", "false").increment();
                String reasonTag = evalResult.bypassReason() != null ? evalResult.bypassReason().name() : "UNKNOWN";
                meterRegistry.counter("ai.cache.semantic.bypasses", "reason", reasonTag).increment();
                return;
            }

            // evalResult.scope() == CacheScope.GENERAL
            meterRegistry.counter("ai.cache.semantic.eligibility", "eligible", "true").increment();

            if (rawPrompt == null || rawPrompt.isBlank()) {
                return;
            }

            String canonical = toCanonicalPrompt(rawPrompt);
            String sha256 = computeSha256(canonical);

            String effectiveUser = (userId != null && !userId.isBlank()) ? userId : "anonymous";
            String existingUser = repeat30mCache.getIfPresent(sha256);

            if (existingUser != null) {
                if (existingUser.equals(effectiveUser)) {
                    // Cùng một người dùng gửi lại câu hỏi giống nhau trong 30 phút
                    meterRegistry.counter("ai.cache.self_repeat_hits").increment();
                } else {
                    // Người dùng khác gửi câu hỏi giống hệt -> Cơ hội vàng cho Semantic Cache
                    meterRegistry.counter("ai.cache.exact_repeat_hits").increment();
                }
            } else {
                repeat30mCache.put(sha256, effectiveUser);
            }
        } catch (Exception e) {
            log.warn("[Telemetry] Fail-safe: Lỗi khi ghi nhận telemetry prompt: {}", e.getMessage());
        }
    }

    /**
     * Phương thức tiện ích tương thích ngược khi chỉ truyền rawPrompt và cờ isGeneralScope.
     */
    public void recordPromptTelemetry(String rawPrompt, boolean isGeneralScope) {
        recordPromptTelemetry(
                null,
                rawPrompt,
                isGeneralScope ? CacheEvaluationResult.general() : CacheEvaluationResult.bypass(CacheBypassReason.NONE)
        );
    }

    /**
     * Chuẩn hóa Canonical Prompt:
     * 1. NFC normalization
     * 2. Bỏ dấu tiếng Việt (stripDiacritics)
     * 3. Cắt dấu câu cuối [!?.,;:]
     * 4. Gộp khoảng trắng thừa và trim
     * 5. Lowercase
     */
    public String toCanonicalPrompt(String rawPrompt) {
        if (rawPrompt == null) return "";
        String nfc = Normalizer.normalize(rawPrompt.trim(), Normalizer.Form.NFC);
        String unaccented = stripDiacritics(nfc);
        String noPunct = TRAILING_PUNCTUATION.matcher(unaccented).replaceAll("");
        String singleSpaced = MULTI_WHITESPACE.matcher(noPunct).replaceAll(" ");
        return singleSpaced.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Băm SHA-256 chuỗi canonical prompt để làm khóa tra cứu trong Caffeine.
     */
    public String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private String stripDiacritics(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_DIACRITICAL_MARKS.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }

    public long getCacheSize() {
        return repeat30mCache.estimatedSize();
    }

    public void clearCache() {
        repeat30mCache.invalidateAll();
    }
}
