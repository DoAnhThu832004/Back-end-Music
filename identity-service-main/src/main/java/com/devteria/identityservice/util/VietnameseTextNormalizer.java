package com.devteria.identityservice.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiện ích chuẩn hóa văn bản tiếng Việt phục vụ tìm kiếm không dấu (Accent-Insensitive Search).
 * Chuyển đổi mọi ký tự có dấu thành ký tự ASCII tương ứng, chuyển chữ thường và chuẩn hóa khoảng trắng.
 *
 * Ví dụ:
 * - "Sơn Tùng M-TP" -> "son tung m-tp"
 * - "Đen Vâu - Trốn Tìm" -> "den vau - tron tim"
 * - "Có Đôi Điều" -> "co doi dieu"
 */
public final class VietnameseTextNormalizer {

    private static final Pattern DIACRITICS_PATTERN = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private VietnameseTextNormalizer() {
        // Utility class
    }

    /**
     * Chuẩn hóa chuỗi tiếng Việt thành chuỗi không dấu, viết thường và thu gọn khoảng trắng.
     *
     * @param input Chuỗi văn bản tiếng Việt gốc
     * @return Chuỗi ASCII không dấu đã chuẩn hóa, hoặc rỗng nếu input null/blank
     */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        // 1. Chuyển chữ thường
        String lower = input.toLowerCase();

        // 2. Xử lý ký tự 'đ' / 'Đ' đặc thù của tiếng Việt (không bị tách bởi Normalizer NFD)
        lower = lower.replace('đ', 'd').replace('Đ', 'd').replace('ð', 'd');

        // 3. Tách các dấu thanh/mũ ra thành Combining Diacritical Marks
        String nfd = Normalizer.normalize(lower, Normalizer.Form.NFD);

        // 4. Xóa toàn bộ dấu thanh/mũ
        String ascii = DIACRITICS_PATTERN.matcher(nfd).replaceAll("");

        // 5. Chuẩn hóa khoảng trắng dư thừa
        return MULTI_SPACE_PATTERN.matcher(ascii.trim()).replaceAll(" ");
    }

    /**
     * Kiểm tra xem chuỗi nguồn có chứa chuỗi tìm kiếm hay không mà không phân biệt hoa thường và dấu tiếng Việt.
     *
     * @param source Chuỗi nguồn (ví dụ tên bài hát trong DB)
     * @param query Chuỗi tìm kiếm người dùng nhập (có dấu hoặc không dấu)
     * @return true nếu source chứa query sau khi đã chuẩn hóa
     */
    public static boolean containsIgnoreCaseAndAccent(String source, String query) {
        if (source == null || query == null || query.isBlank()) {
            return false;
        }
        String normalizedSource = normalize(source);
        String normalizedQuery = normalize(query);
        return normalizedSource.contains(normalizedQuery);
    }

    /**
     * Tách chuỗi thành danh sách các từ khóa không dấu riêng biệt để phục vụ so khớp đa từ.
     *
     * @param input Chuỗi văn bản cần tách từ
     * @return Danh sách các từ khóa không dấu
     */
    public static List<String> tokenize(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(normalized.split(" "));
    }
}
