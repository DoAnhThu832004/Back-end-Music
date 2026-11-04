package com.devteria.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record AiChatStreamRequest(
        @NotBlank(message = "Session ID không được để trống")
        String sessionId,

        @NotBlank(message = "Tin nhắn không được để trống")
        @Size(min = 1, max = 1000, message = "Tin nhắn không được vượt quá 1000 ký tự")
        String message,

        String currentSongId
) {}
