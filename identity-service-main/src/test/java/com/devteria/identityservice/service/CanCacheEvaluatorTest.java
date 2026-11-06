package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CanCacheEvaluator - 8 Cổng Kiểm Định Cache Lớp 7")
class CanCacheEvaluatorTest {

    private CanCacheEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CanCacheEvaluator();
    }

    @Test
    @DisplayName("Cổng 1: Bỏ qua khi câu hỏi rỗng hoặc quá ngắn")
    void testGate1_EmptyOrShortPrompt() {
        assertEquals(CacheBypassReason.EMPTY_PROMPT, evaluator.evaluate(null, null, false).bypassReason());
        assertEquals(CacheBypassReason.EMPTY_PROMPT, evaluator.evaluate("", null, false).bypassReason());
        assertEquals(CacheBypassReason.EMPTY_PROMPT, evaluator.evaluate("   ", null, false).bypassReason());
        assertEquals(CacheBypassReason.EMPTY_PROMPT, evaluator.evaluate("a", null, false).bypassReason());
    }

    @Test
    @DisplayName("Cổng 2: Bỏ qua khi có lịch sử hội thoại trước đó (Multi-turn)")
    void testGate2_HasPriorHistory() {
        CacheEvaluationResult result = evaluator.evaluate("Nhạc Jazz là gì?", null, true);
        assertEquals(CacheScope.BYPASS, result.scope());
        assertEquals(CacheBypassReason.HISTORY, result.bypassReason());
    }

    @Test
    @DisplayName("Cổng 3: Bỏ qua khi đang phát nhạc (Có song context)")
    void testGate3_HasSongContext() {
        CacheEvaluationResult result = evaluator.evaluate("Nhạc Jazz là gì?", "song-123", false);
        assertEquals(CacheScope.BYPASS, result.scope());
        assertEquals(CacheBypassReason.HAS_SONG_CONTEXT, result.bypassReason());
    }

    @Test
    @DisplayName("Cổng 4: Bỏ qua khi chứa thông tin nhận dạng cá nhân PII (SĐT, Email, CCCD)")
    void testGate4_PiiDetection() {
        // Phone
        assertEquals(CacheBypassReason.PII, evaluator.evaluate("Gọi cho tôi số 0912345678", null, false).bypassReason());
        assertEquals(CacheBypassReason.PII, evaluator.evaluate("Liên hệ +84987654321 để nghe nhạc", null, false).bypassReason());

        // Email
        assertEquals(CacheBypassReason.PII, evaluator.evaluate("Gửi playlist qua user@example.com", null, false).bypassReason());

        // CCCD
        assertEquals(CacheBypassReason.PII, evaluator.evaluate("Số CCCD 012345678901 của tôi", null, false).bypassReason());
    }

    @Test
    @DisplayName("Cổng 5: Bỏ qua khi chứa dữ liệu cá nhân & sở thích (Personal Preferences - cả có dấu và không dấu)")
    void testGate5_PersonalPreferences() {
        // Có dấu
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("Bật nhạc theo gu của mình đi", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("Phát playlist của tôi", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("Cho tôi bài hát tôi yêu thích", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("Mở danh sách của anh", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("Xem lịch sử nghe của em", null, false).bypassReason());

        // Không dấu
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("bat nhac theo gu cua minh di", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("phat playlist cua toi", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("cho toi bai hat yeu thich cua toi", null, false).bypassReason());
        assertEquals(CacheBypassReason.PERSONAL, evaluator.evaluate("xem lich su nghe cua anh", null, false).bypassReason());
    }

    @Test
    @DisplayName("Cổng 6: Bỏ qua khi chứa đại từ thay thế ngữ cảnh (Anaphora)")
    void testGate6_Anaphora() {
        // Có dấu
        assertEquals(CacheBypassReason.ANAPHORA, evaluator.evaluate("Bài này do ai sáng tác?", null, false).bypassReason());
        assertEquals(CacheBypassReason.ANAPHORA, evaluator.evaluate("Ca khúc đó phát hành năm nào?", null, false).bypassReason());
        assertEquals(CacheBypassReason.ANAPHORA, evaluator.evaluate("Bài vừa rồi tên là gì?", null, false).bypassReason());

        // Không dấu
        assertEquals(CacheBypassReason.ANAPHORA, evaluator.evaluate("bai nay do ai sang tac?", null, false).bypassReason());
        assertEquals(CacheBypassReason.ANAPHORA, evaluator.evaluate("ca khuc do phat hanh nam nao?", null, false).bypassReason());
    }

    @Test
    @DisplayName("Cổng 7: Bỏ qua khi chứa từ khóa thời gian thực (Temporal)")
    void testGate7_Temporal() {
        // Có dấu
        assertEquals(CacheBypassReason.TEMPORAL, evaluator.evaluate("Hôm nay có bài hát gì mới?", null, false).bypassReason());
        assertEquals(CacheBypassReason.TEMPORAL, evaluator.evaluate("Bài hát mới nhất của Sơn Tùng", null, false).bypassReason());
        assertEquals(CacheBypassReason.TEMPORAL, evaluator.evaluate("Top ca khúc tháng này", null, false).bypassReason());

        // Không dấu
        assertEquals(CacheBypassReason.TEMPORAL, evaluator.evaluate("hom nay co bai gi moi", null, false).bypassReason());
        assertEquals(CacheBypassReason.TEMPORAL, evaluator.evaluate("bai hat moi nhat hien tai", null, false).bypassReason());
    }

    @Test
    @DisplayName("Cổng 8: Bỏ qua khi chứa quy chiếu lửng lơ (Dangling References)")
    void testGate8_DanglingReference() {
        // Có dấu
        assertEquals(CacheBypassReason.DANGLING_REF, evaluator.evaluate("Còn bài nào tương tự không?", null, false).bypassReason());
        assertEquals(CacheBypassReason.DANGLING_REF, evaluator.evaluate("Bài tiếp theo là gì?", null, false).bypassReason());
        assertEquals(CacheBypassReason.DANGLING_REF, evaluator.evaluate("Hát tiếp đi bạn", null, false).bypassReason());

        // Không dấu
        assertEquals(CacheBypassReason.DANGLING_REF, evaluator.evaluate("con bai nao tuong tu nua khong", null, false).bypassReason());
        assertEquals(CacheBypassReason.DANGLING_REF, evaluator.evaluate("bai tiep theo la gi", null, false).bypassReason());
    }

    @Test
    @DisplayName("Đủ điều kiện GENERAL khi là câu hỏi kiến thức âm nhạc độc lập, phổ quát")
    void testGeneralScope_Success() {
        CacheEvaluationResult res1 = evaluator.evaluate("Nhạc Jazz có nguồn gốc từ đâu?", null, false);
        assertEquals(CacheScope.GENERAL, res1.scope());
        assertEquals(CacheBypassReason.NONE, res1.bypassReason());

        CacheEvaluationResult res2 = evaluator.evaluate("Giới thiệu tiểu sử ca sĩ Trịnh Công Sơn", null, false);
        assertEquals(CacheScope.GENERAL, res2.scope());
        assertEquals(CacheBypassReason.NONE, res2.bypassReason());

        CacheEvaluationResult res3 = evaluator.evaluate("Giải thích khái niệm nhịp 4/4 trong âm nhạc", null, false);
        assertEquals(CacheScope.GENERAL, res3.scope());
        assertEquals(CacheBypassReason.NONE, res3.bypassReason());
    }
}
