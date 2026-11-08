package com.devteria.identityservice.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafetyBlockCategoryTest {

    @Test
    @DisplayName("Prompt Block: SAFETY ánh xạ đúng category SAFETY dạng Optional")
    void testFromPromptBlockReason_Safety() {
        assertEquals(Optional.of(SafetyBlockCategory.SAFETY), SafetyBlockCategory.fromPromptBlockReason("SAFETY"));
        assertEquals(Optional.of(SafetyBlockCategory.SAFETY), SafetyBlockCategory.fromPromptBlockReason("safety"));
    }

    @Test
    @DisplayName("Prompt Block: BLOCKLIST và PROHIBITED_CONTENT ánh xạ POLICY")
    void testFromPromptBlockReason_Policy() {
        assertEquals(Optional.of(SafetyBlockCategory.POLICY), SafetyBlockCategory.fromPromptBlockReason("BLOCKLIST"));
        assertEquals(Optional.of(SafetyBlockCategory.POLICY), SafetyBlockCategory.fromPromptBlockReason("PROHIBITED_CONTENT"));
    }

    @Test
    @DisplayName("Prompt Block: Giá trị không xác định rơi vào default OTHER; null hoặc blank trả về Optional.empty()")
    void testFromPromptBlockReason_OtherAndNull() {
        assertEquals(Optional.of(SafetyBlockCategory.OTHER), SafetyBlockCategory.fromPromptBlockReason("BLOCK_REASON_UNSPECIFIED"));
        assertEquals(Optional.of(SafetyBlockCategory.OTHER), SafetyBlockCategory.fromPromptBlockReason("OTHER"));
        assertEquals(Optional.of(SafetyBlockCategory.OTHER), SafetyBlockCategory.fromPromptBlockReason("NEW_UNKNOWN_REASON"));
        assertEquals(Optional.empty(), SafetyBlockCategory.fromPromptBlockReason(null));
        assertEquals(Optional.empty(), SafetyBlockCategory.fromPromptBlockReason("   "));
        assertEquals(Optional.empty(), SafetyBlockCategory.fromPromptBlockReason(""));
    }

    @Test
    @DisplayName("Candidate Finish: SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII được ghi nhận")
    void testFromCandidateFinishReason_SafetyAndPolicy() {
        assertEquals(Optional.of(SafetyBlockCategory.SAFETY), SafetyBlockCategory.fromCandidateFinishReason("SAFETY"));
        assertEquals(Optional.of(SafetyBlockCategory.RECITATION), SafetyBlockCategory.fromCandidateFinishReason("RECITATION"));
        assertEquals(Optional.of(SafetyBlockCategory.POLICY), SafetyBlockCategory.fromCandidateFinishReason("BLOCKLIST"));
        assertEquals(Optional.of(SafetyBlockCategory.POLICY), SafetyBlockCategory.fromCandidateFinishReason("PROHIBITED_CONTENT"));
        assertEquals(Optional.of(SafetyBlockCategory.PRIVACY), SafetyBlockCategory.fromCandidateFinishReason("SPII"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"STOP", "MAX_TOKENS", "OTHER", "LANGUAGE", "MALFORMED_FUNCTION_CALL", "unknown_reason"})
    @DisplayName("Candidate Finish: STOP, MAX_TOKENS, OTHER, LANGUAGE tuyệt đối không tính là safety block")
    void testFromCandidateFinishReason_NonSafetyIgnored(String nonSafetyReason) {
        assertEquals(Optional.empty(), SafetyBlockCategory.fromCandidateFinishReason(nonSafetyReason));
        assertEquals(Optional.empty(), SafetyBlockCategory.fromCandidateFinishReason(null));
        assertEquals(Optional.empty(), SafetyBlockCategory.fromCandidateFinishReason(""));
    }

    @Test
    @DisplayName("fromGemini (Deprecated): Tổng quát tương thích ngược có cảnh báo")
    @SuppressWarnings("deprecation")
    void testFromGemini_Deprecated() {
        assertEquals(SafetyBlockCategory.SAFETY, SafetyBlockCategory.fromGemini("SAFETY"));
        assertEquals(SafetyBlockCategory.RECITATION, SafetyBlockCategory.fromGemini("RECITATION"));
        assertEquals(SafetyBlockCategory.POLICY, SafetyBlockCategory.fromGemini("BLOCKLIST"));
        assertEquals(SafetyBlockCategory.POLICY, SafetyBlockCategory.fromGemini("PROHIBITED_CONTENT"));
        assertEquals(SafetyBlockCategory.PRIVACY, SafetyBlockCategory.fromGemini("SPII"));
        assertEquals(SafetyBlockCategory.OTHER, SafetyBlockCategory.fromGemini("UNRECOGNIZED_ENUM"));
        assertEquals(SafetyBlockCategory.OTHER, SafetyBlockCategory.fromGemini(null));
    }
}
