package com.devteria.identityservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO đại diện cho lệnh điều khiển trình phát từ AI (PLAY hoặc QUEUE).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerActionDto implements Serializable {
    private String action; // "PLAY" hoặc "QUEUE"
    private SongCardDto song;
    private boolean autoExecute; // true nếu người dùng ra lệnh trực tiếp (bật/phát/mở bài)
}
