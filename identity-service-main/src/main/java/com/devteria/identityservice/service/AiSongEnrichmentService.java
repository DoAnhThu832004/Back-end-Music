package com.devteria.identityservice.service;

import com.devteria.identityservice.dto.response.SongAiEnrichmentDto;
import com.devteria.identityservice.dto.response.SongResponse;
import com.devteria.identityservice.entity.Artist;
import com.devteria.identityservice.entity.Genre;
import com.devteria.identityservice.entity.Song;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.GenreRepository;
import com.devteria.identityservice.repository.SongRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AiSongEnrichmentService {

    SongRepository songRepository;
    GenreRepository genreRepository;
    GeminiClientService geminiClientService;

    /**
     * Cháº¡y ngáº§m (@Async) trong ThreadPool riĂªng (aiTaskExecutor).
     * Tuyá»‡t Ä‘á»‘i khĂ´ng cháº·n luá»“ng upload cá»§a Admin.
     */
    @Async("aiTaskExecutor")
    public void enrichSongMetadataAsync(String songId) {
        log.info("Báº¯t Ä‘áº§u tĂ¡c vá»¥ AI phĂ¢n tĂ­ch vĂ  lĂ m giĂ u metadata bĂ i hĂ¡t ngáº§m cho songId: {}", songId);
        try {
            enrichSongMetadata(songId, false);
        } catch (Exception e) {
            log.warn("TĂ¡c vá»¥ enrichSongMetadataAsync cho songId: {} tháº¥t báº¡i (Fail-Open): {}", songId, e.getMessage());
        }
    }

    /**
     * PhÆ°Æ¡ng thá»©c lĂ m giĂ u metadata (dĂ¹ng cho cáº£ Async láº«n nĂºt báº¥m thá»§ cĂ´ng cá»§a Admin).
     */
    @Transactional
    public SongResponse enrichSongMetadata(String songId, boolean overwrite) {
        Song song = songRepository.findWithDetailsById(songId)
                .orElseThrow(() -> new AppException(ErrorCode.SONG_NOT_EXISTED));

        boolean hasDescription = song.getDescription() != null && !song.getDescription().isBlank();
        boolean hasGenres = song.getGenres() != null && !song.getGenres().isEmpty();

        if (!overwrite && hasDescription && hasGenres) {
            log.info("BĂ i hĂ¡t [{}] Ä‘Ă£ cĂ³ mĂ´ táº£ vĂ  thá»ƒ loáº¡i Ä‘áº§y Ä‘á»§. Bá» qua lĂ m giĂ u AI.", song.getName());
            return SongService.toSongResponse(song);
        }

        // 1. TrĂ­ch xuáº¥t ngá»¯ cáº£nh nghá»‡ sÄ©
        String artistNames = "ChÆ°a rĂµ";
        String artistBios = "";
        if (song.getArtists() != null && !song.getArtists().isEmpty()) {
            artistNames = song.getArtists().stream()
                    .map(Artist::getName)
                    .collect(Collectors.joining(", "));
            artistBios = song.getArtists().stream()
                    .map(Artist::getDescription)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("; "));
        }

        // 2. Láº¥y danh sĂ¡ch Genre há»£p lá»‡ cĂ³ sáºµn tá»« MySQL DB Ä‘á»ƒ Ä‘Æ°a vĂ o Schema
        List<Genre> allAvailableGenres = genreRepository.findAll();
        List<String> genreNames = allAvailableGenres.stream()
                .map(Genre::getName)
                .toList();

        // 3. XĂ¢y dá»±ng dynamic responseSchema
        Map<String, Object> responseSchema = new LinkedHashMap<>();
        responseSchema.put("type", "OBJECT");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("shortDescription", Map.of("type", "STRING"));

        Map<String, Object> genreItems = new LinkedHashMap<>();
        genreItems.put("type", "STRING");
        if (!genreNames.isEmpty()) {
            genreItems.put("enum", genreNames);
        }
        properties.put("suggestedGenres", Map.of(
                "type", "ARRAY",
                "items", genreItems
        ));

        properties.put("moods", Map.of(
                "type", "ARRAY",
                "items", Map.of("type", "STRING")
        ));

        responseSchema.put("properties", properties);
        responseSchema.put("required", List.of("shortDescription", "suggestedGenres", "moods"));

        // 4. Prompt templates
        String systemInstruction = """
                Báº¡n lĂ  má»™t chuyĂªn gia phĂª bĂ¬nh Ă¢m nháº¡c vĂ  nhĂ  sáº£n xuáº¥t Ă¢m nháº¡c chuyĂªn nghiá»‡p.
                Nhiá»‡m vá»¥ cá»§a báº¡n lĂ  phĂ¢n tĂ­ch thĂ´ng tin bĂ i hĂ¡t vĂ  tráº£ vá»:
                1. shortDescription: Äoáº¡n mĂ´ táº£ truyá»n cáº£m báº±ng tiáº¿ng Viá»‡t (50-80 tá»«) lĂ m ná»•i báº­t phong cĂ¡ch giai Ä‘iá»‡u vĂ  ca tá»«.
                2. suggestedGenres: Danh sĂ¡ch cĂ¡c thá»ƒ loáº¡i phĂ¹ há»£p nháº¥t Ä‘Æ°á»£c chá»n CHĂNH XĂC tá»« danh sĂ¡ch thá»ƒ loáº¡i cĂ³ sáºµn trong há»‡ thá»‘ng.
                3. moods: 3-5 tá»« khĂ³a tĂ¢m tráº¡ng Ă¢m nháº¡c (vĂ­ dá»¥: Vui tÆ°Æ¡i, LĂ£ng máº¡n, ThÆ° giĂ£n, SĂ´i Ä‘á»™ng, Buá»“n da diáº¿t).
                """;

        String userPrompt = String.format("""
                TĂªn bĂ i hĂ¡t: "%s"
                Nghá»‡ sÄ© thá»ƒ hiá»‡n: "%s"
                Tiá»ƒu sá»­/Phong cĂ¡ch nghá»‡ sÄ©: "%s"
                Danh sĂ¡ch thá»ƒ loáº¡i cĂ³ sáºµn trong há»‡ thá»‘ng: %s
                """, song.getName(), artistNames, artistBios, genreNames);

        // 5. Gá»i Gemini Client Service
        Optional<SongAiEnrichmentDto> enrichmentOpt = geminiClientService.generateStructuredOutput(
                userPrompt,
                systemInstruction,
                responseSchema,
                SongAiEnrichmentDto.class
        );

        if (enrichmentOpt.isEmpty()) {
            log.warn("KhĂ´ng nháº­n Ä‘Æ°á»£c dá»¯ liá»‡u lĂ m giĂ u tá»« Gemini cho bĂ i hĂ¡t [{}] (Fail-Open)", song.getName());
            return SongService.toSongResponse(song);
        }

        SongAiEnrichmentDto dto = enrichmentOpt.get();

        // 6. Cáº­p nháº­t description náº¿u Ä‘ang trá»‘ng hoáº·c overwrite = true
        if (overwrite || !hasDescription) {
            song.setDescription(dto.shortDescription());
        }

        // 7. GĂ¡n thá»ƒ loáº¡i (Genres) khá»›p vá»›i DB
        if (dto.suggestedGenres() != null && !dto.suggestedGenres().isEmpty()) {
            Set<Genre> existingGenres = song.getGenres() != null ? new HashSet<>(song.getGenres()) : new HashSet<>();
            Set<String> suggestedSet = new HashSet<>(dto.suggestedGenres());

            for (Genre g : allAvailableGenres) {
                if (suggestedSet.stream().anyMatch(s -> s.equalsIgnoreCase(g.getName()))) {
                    existingGenres.add(g);
                }
            }
            song.setGenres(existingGenres);
        }

        Song savedSong = songRepository.save(song);
        log.info("LĂ m giĂ u AI hoĂ n táº¥t cho bĂ i hĂ¡t [{}]: MĂ´ táº£ Ä‘Ă£ cáº­p nháº­t, sá»‘ thá»ƒ loáº¡i: {}",
                savedSong.getName(), savedSong.getGenres() != null ? savedSong.getGenres().size() : 0);

        return SongService.toSongResponse(savedSong);
    }
}