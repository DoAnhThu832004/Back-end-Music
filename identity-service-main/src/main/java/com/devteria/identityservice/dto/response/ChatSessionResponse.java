package com.devteria.identityservice.dto.response;

import com.devteria.identityservice.enums.AiPersona;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionResponse {
    private String id;
    private String title;
    private AiPersona persona;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
