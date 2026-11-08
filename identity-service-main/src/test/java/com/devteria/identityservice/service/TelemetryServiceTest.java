package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelemetryService - Thu thập Số liệu & Nhận diện Lặp lại Độc lập Người dùng")
class TelemetryServiceTest {

    private MeterRegistry meterRegistry;
    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        telemetryService = new TelemetryService(meterRegistry);
    }

    @Test
    @DisplayName("Chuẩn hóa Canonical Prompt: Bỏ dấu, cắt dấu câu cuối, gộp khoảng trắng thừa")
    void testCanonicalPromptNormalization() {
        String raw1 = "  Nhạc Jazz Là Gì??? ";
        String canonical1 = telemetryService.toCanonicalPrompt(raw1);
        assertEquals("nhac jazz la gi", canonical1);

        String raw2 = "   Giới thiệu   ca sĩ    Sơn Tùng M-TP....  ";
        String canonical2 = telemetryService.toCanonicalPrompt(raw2);
        assertEquals("gioi thieu ca si son tung m-tp", canonical2);

        String raw3 = "Hòa âm phối khí bài hát!";
        String canonical3 = telemetryService.toCanonicalPrompt(raw3);
        assertEquals("hoa am phoi khi bai hat", canonical3);
    }

    @Test
    @DisplayName("Tính toán SHA-256 nhất quán cho chuỗi canonical")
    void testComputeSha256() {
        String hash1 = telemetryService.computeSha256("nhac jazz la gi");
        String hash2 = telemetryService.computeSha256("nhac jazz la gi");
        String hash3 = telemetryService.computeSha256("rock and roll");

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
    }

    @Test
    @DisplayName("Ghi nhận Telemetry cho prompt bị BYPASS: Tăng bộ đếm bypass kèm lý do")
    void testRecordPromptTelemetry_Bypass() {
        CacheEvaluationResult piiResult = CacheEvaluationResult.bypass(CacheBypassReason.PII);
        telemetryService.recordPromptTelemetry("user1", "Số điện thoại 0912345678", piiResult);

        Counter notEligibleCounter = meterRegistry.find("ai.cache.semantic.eligibility")
                .tag("eligible", "false")
                .counter();
        assertNotNull(notEligibleCounter);
        assertEquals(1.0, notEligibleCounter.count());

        Counter bypassReasonCounter = meterRegistry.find("ai.cache.semantic.bypasses")
                .tag("reason", "PII")
                .counter();
        assertNotNull(bypassReasonCounter);
        assertEquals(1.0, bypassReasonCounter.count());
    }

    @Test
    @DisplayName("Ghi nhận Telemetry cho prompt GENERAL: Tăng bộ đếm eligible=true")
    void testRecordPromptTelemetry_GeneralEligible() {
        CacheEvaluationResult generalResult = CacheEvaluationResult.general();
        telemetryService.recordPromptTelemetry("user1", "Nhạc Pop là gì?", generalResult);

        Counter eligibleCounter = meterRegistry.find("ai.cache.semantic.eligibility")
                .tag("eligible", "true")
                .counter();
        assertNotNull(eligibleCounter);
        assertEquals(1.0, eligibleCounter.count());
    }

    @Test
    @DisplayName("Phân biệt chính xác giữa lặp lại liên người dùng và cùng người dùng thử lại (cả cửa sổ 30m và 24h)")
    void testCrossUserVsSameUserRepeat() {
        CacheEvaluationResult generalResult = CacheEvaluationResult.general();
        String prompt = "Nguồn gốc nhạc Jazz?";

        // Lượt 1: User A gửi câu hỏi lần đầu -> Lưu vào cache 30m và 24h
        telemetryService.recordPromptTelemetry("userA", prompt, generalResult);
        assertEquals(1, telemetryService.getCacheSize());
        assertEquals(1, telemetryService.getCache24hSize());
        assertNull(meterRegistry.find("ai.cache.exact_repeat_hits").counter());
        assertNull(meterRegistry.find("ai.cache.self_repeat_hits").counter());

        // Lượt 2: User A gửi lại đúng câu hỏi đó -> self_repeat_hits tăng
        telemetryService.recordPromptTelemetry("userA", prompt, generalResult);
        Counter selfRepeatCounter = meterRegistry.find("ai.cache.self_repeat_hits").counter();
        assertNotNull(selfRepeatCounter);
        assertEquals(1.0, selfRepeatCounter.count());
        Counter selfRepeat24hCounter = meterRegistry.find("ai.cache.self_repeat_24h_hits").counter();
        assertNotNull(selfRepeat24hCounter);
        assertEquals(1.0, selfRepeat24hCounter.count());

        // Lượt 3: User B (người dùng khác) gửi câu hỏi tương tự (sau khi canonicalize giống hệt)
        String promptUserB = "  nguồn gốc NHẠC JAZZ???  ";
        telemetryService.recordPromptTelemetry("userB", promptUserB, generalResult);
        Counter exactRepeatCounter = meterRegistry.find("ai.cache.exact_repeat_hits").counter();
        assertNotNull(exactRepeatCounter);
        assertEquals(1.0, exactRepeatCounter.count());
        Counter exactRepeat24hCounter = meterRegistry.find("ai.cache.exact_repeat_24h_hits").counter();
        assertNotNull(exactRepeat24hCounter);
        assertEquals(1.0, exactRepeat24hCounter.count());
    }

    @Test
    @DisplayName("Cô lập Persona trong khóa hash: Câu hỏi giống nhau khác Persona không bị tính lặp chéo")
    void testPersonaIsolationInRepeatKey() {
        CacheEvaluationResult generalResult = CacheEvaluationResult.general();
        String prompt = "Phân tích điệu Blues 12 ô nhịp";

        // User A hỏi với persona GENZ_DJ
        telemetryService.recordPromptTelemetry("userA", prompt, AiPersona.GENZ_DJ, generalResult);
        assertEquals(1, telemetryService.getCacheSize());

        // User B hỏi đúng câu đó nhưng với persona MUSIC_CRITIC -> Persona khác nhau nên không được hit cache chéo
        telemetryService.recordPromptTelemetry("userB", prompt, AiPersona.MUSIC_CRITIC, generalResult);
        assertEquals(2, telemetryService.getCacheSize());
        assertNull(meterRegistry.find("ai.cache.exact_repeat_hits").counter());

        // User C hỏi đúng câu đó với persona GENZ_DJ -> Cùng persona GENZ_DJ nên tính exact_repeat_hits
        telemetryService.recordPromptTelemetry("userC", prompt, AiPersona.GENZ_DJ, generalResult);
        Counter exactRepeatCounter = meterRegistry.find("ai.cache.exact_repeat_hits").counter();
        assertNotNull(exactRepeatCounter);
        assertEquals(1.0, exactRepeatCounter.count());
    }

    @Test
    @DisplayName("Người dùng ẩn danh (userId null/blank) được gắn nhãn anonymous_prompts và không làm ô nhiễm repeat cache")
    void testAnonymousPromptsTrackedSeparatelyWithoutPollutingRepeatHits() {
        CacheEvaluationResult generalResult = CacheEvaluationResult.general();
        String prompt = "Giải thích thang âm ngũ cung";

        // Người dùng ẩn danh gửi câu hỏi
        telemetryService.recordPromptTelemetry(null, prompt, generalResult);
        telemetryService.recordPromptTelemetry("   ", prompt, generalResult);

        // Cache lặp lại không bị ghi đè/ô nhiễm
        assertEquals(0, telemetryService.getCacheSize());
        assertEquals(0, telemetryService.getCache24hSize());

        // Metric anonymous_prompts được tăng 2 lần
        Counter anonCounter = meterRegistry.find("ai.cache.anonymous_prompts").counter();
        assertNotNull(anonCounter);
        assertEquals(2.0, anonCounter.count());

        // Tuyệt đối không sinh metric exact_repeat_hits hoặc self_repeat_hits giả tạo
        assertNull(meterRegistry.find("ai.cache.exact_repeat_hits").counter());
        assertNull(meterRegistry.find("ai.cache.self_repeat_hits").counter());
    }

    @Test
    @DisplayName("Đảm bảo an toàn Fail-safe khi tham số bất thường hoặc null")
    void testFailSafeResilience() {
        assertDoesNotThrow(() -> {
            telemetryService.recordPromptTelemetry(null, null, null);
            telemetryService.recordPromptTelemetry("user1", null, CacheEvaluationResult.general());
            telemetryService.recordPromptTelemetry("user1", "", CacheEvaluationResult.general());
            telemetryService.recordPromptTelemetry("user1", "   ", CacheEvaluationResult.general());
        });

        // Test với service không có MeterRegistry
        TelemetryService noRegistryService = new TelemetryService(null);
        assertDoesNotThrow(() -> {
            noRegistryService.recordPromptTelemetry("user1", "Nhạc Rock", CacheEvaluationResult.general());
        });
    }
}
