package com.devteria.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO đại diện cho đề xuất danh sách phát (Playlist) từ AI gửi xuống Android UI (Human-in-the-Loop).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistProposalDto implements Serializable {
    private String suggestedTitle;
    private List<SongCardDto> songs;
}
