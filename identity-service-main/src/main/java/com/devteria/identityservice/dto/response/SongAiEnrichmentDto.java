package com.devteria.identityservice.dto.response;

import java.util.List;

public record SongAiEnrichmentDto(
    String shortDescription,
    List<String> suggestedGenres,
    List<String> moods
) {}