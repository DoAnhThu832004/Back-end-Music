package com.devteria.identityservice.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VietnameseTextNormalizerTest {

    @Test
    @DisplayName("Normalize standard Vietnamese artist and song names")
    void testNormalize_StandardNames() {
        assertEquals("son tung m-tp", VietnameseTextNormalizer.normalize("Sơn Tùng M-TP"));
        assertEquals("den vau - tron tim", VietnameseTextNormalizer.normalize("Đen Vâu - Trốn Tìm"));
        assertEquals("co doi dieu", VietnameseTextNormalizer.normalize("Có Đôi Điều"));
        assertEquals("khuat loi", VietnameseTextNormalizer.normalize("Khuất Lối"));
        assertEquals("mua thang sau", VietnameseTextNormalizer.normalize("Mưa Tháng Sáu"));
    }

    @Test
    @DisplayName("Normalize handles uppercase and lowercase D with stroke (Đ/đ)")
    void testNormalize_LetterDWithStroke() {
        assertEquals("d", VietnameseTextNormalizer.normalize("Đ"));
        assertEquals("d", VietnameseTextNormalizer.normalize("đ"));
        assertEquals("duong doi song gio", VietnameseTextNormalizer.normalize("Đường Đời Sóng Gió"));
        assertEquals("di vang cuoc tinh", VietnameseTextNormalizer.normalize("Dĩ vãng cuộc tình"));
    }

    @Test
    @DisplayName("Normalize trims and collapses consecutive whitespaces")
    void testNormalize_WhitespaceCollapsing() {
        assertEquals("bai hat mua thu", VietnameseTextNormalizer.normalize("   Bài    hát   mùa   thu   "));
        assertEquals("", VietnameseTextNormalizer.normalize("     "));
        assertEquals("", VietnameseTextNormalizer.normalize(null));
        assertEquals("", VietnameseTextNormalizer.normalize(""));
    }

    @Test
    @DisplayName("Contains ignore case and accent matching")
    void testContainsIgnoreCaseAndAccent() {
        String songName = "Có Đôi Điều";

        assertTrue(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, "co doi dieu"));
        assertTrue(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, "CO DOI DIEU"));
        assertTrue(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, "Có đôi"));
        assertTrue(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, "doi"));
        assertFalse(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, "son tung"));
        assertFalse(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(songName, ""));
        assertFalse(VietnameseTextNormalizer.containsIgnoreCaseAndAccent(null, "test"));
    }

    @Test
    @DisplayName("Tokenize Vietnamese input into list of normalized words")
    void testTokenize() {
        List<String> tokens = VietnameseTextNormalizer.tokenize("Đen Vâu ft. Vũ");
        assertEquals(List.of("den", "vau", "ft.", "vu"), tokens);

        assertTrue(VietnameseTextNormalizer.tokenize("").isEmpty());
        assertTrue(VietnameseTextNormalizer.tokenize(null).isEmpty());
    }
}
