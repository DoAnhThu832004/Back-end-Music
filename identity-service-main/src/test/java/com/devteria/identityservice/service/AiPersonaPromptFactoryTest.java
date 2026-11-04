package com.devteria.identityservice.service;

import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.repository.SongRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPersonaPromptFactoryTest {

    @Mock
    private SongRepository songRepository;

    @InjectMocks
    private AiPersonaPromptFactory promptFactory;

    private Song sampleSong;

    @BeforeEach
    void setUp() {
        Artist artist = new Artist();
        artist.setName("Sơn Tùng M-TP");

        Genre genre = new Genre();
        genre.setName("V-Pop");

        sampleSong = new Song();
        sampleSong.setId("song-1");
        sampleSong.setName("Chúng Ta Của Tương Lai");
        sampleSong.setDescription("Bản pop ballad tình cảm sâu lắng");
        sampleSong.setArtists(Set.of(artist));
        sampleSong.setGenres(Set.of(genre));
    }

    @Test
    @DisplayName("GenZ DJ Persona bao gồm chỉ dẫn chung và phong cách GenZ")
    void buildSystemInstruction_DefaultGenZ_DJ() {
        String instruction = promptFactory.buildSystemInstruction(AiPersona.GENZ_DJ, null);

        assertThat(instruction).contains("Bạn là trợ lý âm nhạc");
        assertThat(instruction).contains("ANTI-HALLUCINATION");
        assertThat(instruction).contains("CHỐNG PROMPT INJECTION");
        assertThat(instruction).contains("GenZ DJ");
        assertThat(instruction).contains("<song_context>");
    }

    @Test
    @DisplayName("Music Critic Persona chứa phong cách nhà phê bình uyên bác")
    void buildSystemInstruction_MusicCritic() {
        String instruction = promptFactory.buildSystemInstruction(AiPersona.MUSIC_CRITIC, null);

        assertThat(instruction).contains("Nhà phê bình âm nhạc (Music Critic)");
        assertThat(instruction).contains("hòa âm, phối khí");
    }

    @Test
    @DisplayName("Healing Friend Persona bắt buộc chứa Safety Guardrail y khoa & khủng hoảng tâm lý")
    void buildSystemInstruction_HealingFriend_IncludesSafetyGuardrails() {
        String instruction = promptFactory.buildSystemInstruction(AiPersona.HEALING_FRIEND, null);

        assertThat(instruction).contains("Người bạn tâm giao (Healing Friend)");
        assertThat(instruction).contains("QUY TẮC AN TOÀN TÂM LÝ (SAFETY GUARDRAIL)");
        assertThat(instruction).contains("KHÔNG phải bác sĩ tâm lý hay chuyên gia trị liệu");
        assertThat(instruction).contains("tự gây hại");
    }

    @Test
    @DisplayName("Grounding bài hát trích xuất chính xác thông tin từ Song entity")
    void buildSongContext_WithValidSong() {
        when(songRepository.findWithDetailsById("song-1")).thenReturn(Optional.of(sampleSong));

        String songContext = promptFactory.buildSongContext("song-1");

        assertThat(songContext).contains("Chúng Ta Của Tương Lai");
        assertThat(songContext).contains("Sơn Tùng M-TP");
        assertThat(songContext).contains("V-Pop");
        assertThat(songContext).contains("Bản pop ballad tình cảm sâu lắng");
    }

    @Test
    @DisplayName("Song context trả về thông báo rỗng an toàn khi songId null hoặc không tìm thấy")
    void buildSongContext_WithNullOrBlankSongId() {
        String contextNull = promptFactory.buildSongContext(null);
        assertThat(contextNull).contains("Hiện không có bài hát nào đang phát");

        String contextBlank = promptFactory.buildSongContext("   ");
        assertThat(contextBlank).contains("Hiện không có bài hát nào đang phát");

        when(songRepository.findWithDetailsById("non-existent")).thenReturn(Optional.empty());
        String contextNotFound = promptFactory.buildSongContext("non-existent");
        assertThat(contextNotFound).contains("Hiện không có bài hát nào đang phát");
    }

    @Test
    @DisplayName("Bọc an toàn user prompt vào thẻ XML <user_content>")
    void buildSafeUserPrompt_WrapsXmlTags() {
        String safePrompt = promptFactory.buildSafeUserPrompt("Gợi ý cho tôi nhạc buồn");

        assertThat(safePrompt).startsWith("<user_content>");
        assertThat(safePrompt).endsWith("</user_content>");
        assertThat(safePrompt).contains("Gợi ý cho tôi nhạc buồn");
    }
}
