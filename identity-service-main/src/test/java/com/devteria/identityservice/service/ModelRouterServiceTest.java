package com.devteria.identityservice.service;

import com.devteria.identityservice.configuration.AiRouterProperties;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.ModelTier;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelRouterServiceTest {

    private AiRouterProperties properties;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private SimpleMeterRegistry meterRegistry;
    private ModelRouterService modelRouterService;

    @BeforeEach
    void setUp() {
        properties = new AiRouterProperties();
        properties.setFastPathEnabled(true);
        properties.setReasoningTierEnabled(true);
        properties.setFastTierModel("gemini-3.5-flash-lite");
        properties.setReasoningTierModel("gemini-2.5-pro");
        properties.setFirstTokenTimeoutMs(11000);

        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        meterRegistry = new SimpleMeterRegistry();

        modelRouterService = new ModelRouterService(properties, circuitBreakerRegistry, meterRegistry);
    }

    @Test
    @DisplayName("Default routing to FAST_TIER for GENZ_DJ persona")
    void testResolveDecision_GenzDj_RoutesToFastTier() {
        String prompt = "Gợi ý cho mình vài bài hát nghe lúc làm việc tràn đầy năng lượng";
        RoutingDecision decision = modelRouterService.resolveDecision(prompt, AiPersona.GENZ_DJ);

        assertEquals(ModelTier.FAST_TIER, decision.tier());
        assertEquals("gemini-3.5-flash-lite", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("Music Critic with short prompt (<= 40 chars) routes to FAST_TIER")
    void testResolveDecision_MusicCritic_ShortPrompt_RoutesToFastTier() {
        String shortPrompt = "Hòa âm bài này thế nào?"; // < 40 ký tự
        RoutingDecision decision = modelRouterService.resolveDecision(shortPrompt, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.FAST_TIER, decision.tier());
        assertEquals("gemini-3.5-flash-lite", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("Music Critic with long prompt (> 40 chars) but no music critique keywords routes to FAST_TIER")
    void testResolveDecision_MusicCritic_NoKeywords_RoutesToFastTier() {
        String promptWithoutKeywords = "Chào chuyên gia, hôm nay tôi muốn nghe một bài hát thật là vui tươi và sôi động";
        assertTrue(promptWithoutKeywords.length() > 40);

        RoutingDecision decision = modelRouterService.resolveDecision(promptWithoutKeywords, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.FAST_TIER, decision.tier());
        assertEquals("gemini-3.5-flash-lite", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("Music Critic with long prompt and accented music theory keywords routes to REASONING_TIER")
    void testResolveDecision_MusicCritic_AccentedKeywords_RoutesToReasoningTier() {
        String accentedPrompt = "Xin hãy phân tích hòa âm và cấu trúc giai điệu của bản giao hưởng này giúp tôi";
        assertTrue(accentedPrompt.length() > 40);

        RoutingDecision decision = modelRouterService.resolveDecision(accentedPrompt, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.REASONING_TIER, decision.tier());
        assertEquals("gemini-2.5-pro", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("Music Critic with long prompt and unaccented keywords routes to REASONING_TIER")
    void testResolveDecision_MusicCritic_UnaccentedKeywords_RoutesToReasoningTier() {
        String unaccentedPrompt = "Lam on hay phan tich nghe thuat va thu phap sang tac trong ca khuc nay nhe";
        assertTrue(unaccentedPrompt.length() > 40);

        RoutingDecision decision = modelRouterService.resolveDecision(unaccentedPrompt, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.REASONING_TIER, decision.tier());
        assertEquals("gemini-2.5-pro", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("CircuitBreaker OPEN state degrades to FAST_TIER with degradedByBreaker = true")
    void testResolveDecision_CircuitBreakerOpen_DegradesToFastTier() {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(ModelRouterService.CIRCUIT_BREAKER_INSTANCE);
        cb.transitionToOpenState();

        String criticPrompt = "Xin hãy phân tích hòa âm và phối khí chi tiết bài hát này giúp tôi";
        RoutingDecision decision = modelRouterService.resolveDecision(criticPrompt, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.FAST_TIER, decision.tier());
        assertEquals("gemini-3.5-flash-lite", decision.targetModel());
        assertTrue(decision.degradedByBreaker(), "Phải đánh dấu degradedByBreaker = true để chặn lưu cache");
        assertFalse(decision.reasoningTierDisabled());
    }

    @Test
    @DisplayName("Admin disables reasoning tier -> Routes to FAST_TIER with reasoningTierDisabled = true (allows cache)")
    void testResolveDecision_ReasoningTierDisabled_RoutesToFastTier() {
        properties.setReasoningTierEnabled(false);

        String criticPrompt = "Xin hãy phân tích hòa âm và phối khí chi tiết bài hát này giúp tôi";
        RoutingDecision decision = modelRouterService.resolveDecision(criticPrompt, AiPersona.MUSIC_CRITIC);

        assertEquals(ModelTier.FAST_TIER, decision.tier());
        assertEquals("gemini-3.5-flash-lite", decision.targetModel());
        assertFalse(decision.degradedByBreaker());
        assertTrue(decision.reasoningTierDisabled(), "reasoningTierDisabled = true không ngăn cản lưu cache");
    }
}
