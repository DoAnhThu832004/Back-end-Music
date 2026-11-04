package com.devteria.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO đại diện cho thẻ bài hát đề xuất từ AI (sử dụng cho Carousel, BottomSheet và Player Actions).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SongCardDto implements Serializable {
    private String id;
    private String name;
    private String artistNames;
    private String genreNames;
    private String description;
    private String imageUrl;
    private String audioUrl;
    private Long duration;
    private Long playCount;
}
