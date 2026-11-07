package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.FastPathResult;
import com.devteria.identityservice.enums.AiPersona;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FastPathEngineTest {

    private static final String SAMPLE_SONG_ID = "song-123";

    @Test
    @DisplayName("Khớp lệnh PAUSE có dấu với từ đệm dài (> 30 ký tự)")
    void evaluate_PauseAccented_WithTrailingParticles() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "tạm dừng nhạc giúp mình với nha",
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(FastPathResult.FastPathType.PLAYER_ACTION);
        assertThat(result.get().playerAction()).isNotNull();
        assertThat(result.get().playerAction().getAction()).isEqualTo("PAUSE");
        assertThat(result.get().playerAction().isAutoExecute()).isTrue();
        assertThat(result.get().responseText()).isEqualTo("Đã tạm dừng phát nhạc.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tạm dừng",
            "dừng lại",
            "tắt nhạc",
            "tắt nhạc đi",
            "ngừng phát nhé",
            "dừng nhạc giúp mình với"
    })
    @DisplayName("Khớp các biến thể lệnh PAUSE có dấu")
    void evaluate_PauseAccented_Variants(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().playerAction().getAction()).isEqualTo("PAUSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tam dung",
            "tam dung nhac",
            "tat",
            "tat nhac",
            "dung lai",
            "ngung phat",
            "stop",
            "pause",
            "tam dung giup minh voi"
    })
    @DisplayName("Khớp các biến thể lệnh PAUSE không dấu")
    void evaluate_PauseUnaccented_Variants(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().playerAction().getAction()).isEqualTo("PAUSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tiếp tục",
            "phát tiếp",
            "tiếp tục đi nhé",
            "tiep tuc",
            "phat tiep",
            "resume"
    })
    @DisplayName("Khớp lệnh RESUME có dấu và không dấu")
    void evaluate_Resume_Variants(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().playerAction().getAction()).isEqualTo("RESUME");
        assertThat(result.get().responseText()).isEqualTo("Đã tiếp tục phát nhạc.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "qua bài",
            "chuyển bài",
            "bài tiếp",
            "next bài",
            "bài sau",
            "bài tiếp theo",
            "qua bai",
            "chuyen bai",
            "next",
            "bai sau",
            "bai tiep theo"
    })
    @DisplayName("Khớp lệnh NEXT có dấu và không dấu - Phản hồi trung tính")
    void evaluate_Next_Variants(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().playerAction().getAction()).isEqualTo("NEXT");
        assertThat(result.get().responseText()).isEqualTo("Đã gửi lệnh chuyển bài.");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "tạm dung",
            "tam dừng",
            "bài tiep",
            "bai tiếp theo"
    })
    @DisplayName("Khớp lệnh gõ nửa nạc nửa mỡ (partially accented) nhờ tầng fallback không dấu")
    void evaluate_PartiallyAccented_FallbackSuccess(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "đừng phát nữa",
            "đừng nhé",
            "không tắt nhạc",
            "chớ dừng lại",
            "thôi đừng tắt",
            "đang nghe đừng dừng",
            "làm ơn không tắt nhạc"
    })
    @DisplayName("Chặn triệt để các câu phủ định (ở đầu và giữa câu) -> Nhường LLM")
    void evaluate_Negation_ShouldBypass(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Chào hỏi ở Turn 1 (hasPriorHistory == false) -> Trả template chào nhanh")
    void evaluate_Greeting_Turn1_Success() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "Xin chào",
                AiPersona.GENZ_DJ,
                null,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(FastPathResult.FastPathType.TEXT_ONLY);
        assertThat(result.get().responseText()).contains("GenZ DJ");
    }

    @Test
    @DisplayName("Chào hỏi ở Turn 2+ (hasPriorHistory == true) -> Bỏ qua để LLM trả lời tự nhiên")
    void evaluate_Greeting_Turn2Plus_ShouldBypass() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "Xin chào",
                AiPersona.GENZ_DJ,
                null,
                true, // Đã có lịch sử hội thoại
                true
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Hỏi danh tính ở Turn 1 -> Trả template danh tính")
    void evaluate_Identity_Turn1_Success() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "Bạn là ai",
                AiPersona.MUSIC_CRITIC,
                null,
                false,
                true
        );

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(FastPathResult.FastPathType.TEXT_ONLY);
        assertThat(result.get().responseText()).contains("Chuyên gia Phê bình Âm nhạc");
    }

    @Test
    @DisplayName("Lệnh Player khi currentSongId == null -> Bỏ qua nhường LLM")
    void evaluate_PlayerAction_NoCurrentSong_ShouldBypass() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "tạm dừng",
                AiPersona.GENZ_DJ,
                null, // Không có bài đang phát
                false,
                true
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Lệnh Player khi client cũ (clientSupportsTransportActions == false) -> Bỏ qua nhường LLM")
    void evaluate_PlayerAction_LegacyClient_ShouldBypass() {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                "tạm dừng",
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                false // Client cũ không hỗ trợ transport actions
        );

        assertThat(result).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Dũng",
            "Dụng",
            "Đặng"
    })
    @DisplayName("Chống va chạm dấu với tên riêng (Dũng, Dụng) -> Bỏ qua")
    void evaluate_DiacriticCollisions_ShouldBypass(String prompt) {
        Optional<FastPathResult> result = FastPathEngine.evaluate(
                prompt,
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Chặn câu quá dài (> 60 ký tự) hoặc chứa tên bài hát/ca sĩ")
    void evaluate_OverLengthOrSpecificSong_ShouldBypass() {
        Optional<FastPathResult> overLength = FastPathEngine.evaluate(
                "tạm dừng bài hát này ngay lập tức cho mình với nhé bạn trợ lý âm nhạc thân yêu ơi",
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );
        assertThat(overLength).isEmpty();

        Optional<FastPathResult> withArtist = FastPathEngine.evaluate(
                "tắt nhạc của Sơn Tùng",
                AiPersona.GENZ_DJ,
                SAMPLE_SONG_ID,
                false,
                true
        );
        assertThat(withArtist).isEmpty();
    }
}
