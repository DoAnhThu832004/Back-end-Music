package com.devteria.identityservice.dto.response;

import com.devteria.identityservice.enums.MessageSender;
import com.devteria.identityservice.enums.MessageStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String id;
    private String sessionId;
    private MessageSender sender;
    private MessageStatus status;
    private String content;
    private String currentSongId;
    private String currentSongName;
    private int tokenCount;
    private LocalDateTime createdAt;
}
