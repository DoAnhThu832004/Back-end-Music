package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import com.devteria.identityservice.util.VietnameseTextNormalizer;
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
 * - Theo dõi tỉ lệ câu hỏi lặp lại trong cửa sổ trượt 30 phút và 24 giờ (Caffeine 50.000 mục).
 * - Phân biệt chính xác giữa lặp lại liên người dùng (exact_repeat_hits) và người dùng tự thử lại (self_repeat_hits).
 * - Tách biệt câu hỏi từ người dùng ẩn danh (anonymous_prompts) để tránh làm sai lệch tỉ lệ lặp.
 * - Cô lập Persona trong khóa hash (persona:canonical) nhằm đảm bảo nguyên tắc Persona Isolation.
 * - Chuẩn hóa Canonical Prompt (NFC, bỏ dấu, cắt dấu câu cuối, gộp khoảng trắng thừa, SHA-256).
 * - Hoàn toàn fail-safe: Không bao giờ làm gián đoạn luồng chat của người dùng.
 */
@Service
@Slf4j
public class TelemetryService {

    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[!?.,;:]+$");
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private final MeterRegistry meterRegistry;
    // Lưu key: sha256(persona:canonicalPrompt), value: userId
    private final Cache<String, String> repeat30mCache;
    private final Cache<String, String> repeat24hCache;

    public TelemetryService(@Autowired(required = false) MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.repeat30mCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(50_000)
                .build();
        this.repeat24hCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(50_000)
                .build();
    }

    /**
     * Ghi nhận Telemetry tương thích khi không truyền Persona.
     */
    public void recordPromptTelemetry(String userId, String rawPrompt, CacheEvaluationResult evalResult) {
        recordPromptTelemetry(userId, rawPrompt, null, evalResult);
    }

    /**
     * Ghi nhận Telemetry cho một lượt tương tác của người dùng kèm Persona.
     * Hoàn toàn bọc trong khối try-catch để đảm bảo tính an toàn (fail-safe).
     */
    public void recordPromptTelemetry(String userId, String rawPrompt, AiPersona persona, CacheEvaluationResult evalResult) {
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

            // Xử lý người dùng ẩn danh: Không đưa vào repeat cache để tránh làm lệch thống kê liên người dùng
            if (userId == null || userId.isBlank()) {
                meterRegistry.counter("ai.cache.anonymous_prompts").increment();
                return;
            }

            String canonical = toCanonicalPrompt(rawPrompt);
            String personaPrefix = (persona != null) ? persona.name() : "GLOBAL";
            String cacheKey = personaPrefix + ":" + canonical;
            String sha256 = computeSha256(cacheKey);

            // 1. Đánh giá cửa sổ trượt 30 phút
            String existingUser30m = repeat30mCache.getIfPresent(sha256);
            if (existingUser30m != null) {
                if (existingUser30m.equals(userId)) {
                    // Cùng một người dùng gửi lại câu hỏi giống nhau trong 30 phút
                    meterRegistry.counter("ai.cache.self_repeat_hits").increment();
                } else {
                    // Người dùng khác gửi câu hỏi giống hệt trong cùng Persona -> Cơ hội vàng cho Semantic Cache
                    meterRegistry.counter("ai.cache.exact_repeat_hits").increment();
                }
            } else {
                repeat30mCache.put(sha256, userId);
            }

            // 2. Đánh giá cửa sổ trượt 24 giờ (đo lường hit rate tiềm năng thực tế của Cache)
            String existingUser24h = repeat24hCache.getIfPresent(sha256);
            if (existingUser24h != null) {
                if (existingUser24h.equals(userId)) {
                    meterRegistry.counter("ai.cache.self_repeat_24h_hits").increment();
                } else {
                    meterRegistry.counter("ai.cache.exact_repeat_24h_hits").increment();
                }
            } else {
                repeat24hCache.put(sha256, userId);
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
                null,
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
        String unaccented = VietnameseTextNormalizer.stripDiacritics(nfc);
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

    public long getCacheSize() {
        return repeat30mCache.estimatedSize();
    }

    public long getCache24hSize() {
        return repeat24hCache.estimatedSize();
    }

    public void clearCache() {
        repeat30mCache.invalidateAll();
        repeat24hCache.invalidateAll();
    }
}
