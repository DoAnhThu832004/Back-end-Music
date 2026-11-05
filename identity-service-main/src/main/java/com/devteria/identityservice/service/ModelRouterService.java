package com.devteria.identityservice.service;

import com.devteria.identityservice.configuration.AiRouterProperties;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.ModelTier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ModelRouterService {

    public static final String CIRCUIT_BREAKER_INSTANCE = "geminiCriticRouter";

    private static final Pattern MUSIC_CRITIC_KEYWORDS = Pattern.compile(
            "\\b(?:hoa am|phoi khi|cau truc giai dieu|phan tich nghe thuat|thu phap sang tac|so sanh phong cach|vocal technique)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern COMBINING_DIACRITICAL_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

    private final AiRouterProperties routerProperties;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ModelRouterService(
            AiRouterProperties routerProperties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            @Autowired(required = false) MeterRegistry meterRegistry) {
        this.routerProperties = routerProperties;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Phân làn mô hình xử lý (Model Routing Decision):
     * - Mặc định: FAST_TIER (Flash-Lite)
     * - REASONING_TIER (Pro): Khi persona là MUSIC_CRITIC, prompt > 40 ký tự và chứa từ khóa nhạc lý chuyên sâu.
     * - Kiểm tra CircuitBreaker: Nếu mạch OPEN hoặc FORCED_OPEN, hạ cấp về FAST_TIER với cờ degradedByBreaker = true (chặn cache).
     * - Nếu admin chủ động tắt reasoning tier: trả về FAST_TIER với cờ reasoningTierDisabled = true (cho phép cache).
     */
    public RoutingDecision resolveDecision(String rawPrompt, AiPersona persona) {
        boolean qualifiesForCritic = isMusicCriticTask(rawPrompt, persona);

        RoutingDecision decision;
        if (qualifiesForCritic) {
            if (!routerProperties.isReasoningTierEnabled()) {
                log.info("[Model Router] Yêu cầu Critic thỏa mãn nhưng reasoningTierEnabled = false. Điều hướng FAST_TIER.");
                decision = new RoutingDecision(
                        ModelTier.FAST_TIER,
                        routerProperties.getFastTierModel(),
                        false,
                        true
                );
            } else {
                CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_INSTANCE);
                CircuitBreaker.State state = cb.getState();

                if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                    log.warn("[Model Router] Circuit Breaker {} đang {}, hạ cấp tạm thời sang FAST_TIER.",
                            CIRCUIT_BREAKER_INSTANCE, state);
                    decision = new RoutingDecision(
                            ModelTier.FAST_TIER,
                            routerProperties.getFastTierModel(),
                            true,
                            false
                    );
                } else {
                    log.info("[Model Router] Điều hướng sang REASONING_TIER ({}) cho Critic prompt.",
                            routerProperties.getReasoningTierModel());
                    decision = new RoutingDecision(
                            ModelTier.REASONING_TIER,
                            routerProperties.getReasoningTierModel(),
                            false,
                            false
                    );
                }
            }
        } else {
            decision = new RoutingDecision(
                    ModelTier.FAST_TIER,
                    routerProperties.getFastTierModel(),
                    false,
                    false
            );
        }

        if (meterRegistry != null) {
            meterRegistry.counter("ai.router.routing",
                    "tier", decision.tier().name(),
                    "model", decision.targetModel()
            ).increment();
        }

        return decision;
    }

    public boolean isMusicCriticTask(String rawPrompt, AiPersona persona) {
        if (persona != AiPersona.MUSIC_CRITIC) {
            return false;
        }
        if (rawPrompt == null || rawPrompt.trim().length() <= 40) {
            return false;
        }

        String unaccented = stripDiacritics(rawPrompt.trim().toLowerCase(Locale.ROOT));
        return MUSIC_CRITIC_KEYWORDS.matcher(unaccented).find();
    }

    public static String stripDiacritics(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = COMBINING_DIACRITICAL_MARKS.matcher(normalized).replaceAll("");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }
}
