package com.devteria.identityservice.dto.request;

import com.devteria.identityservice.enums.AiPersona;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSessionCreationRequest {
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;
    
    @Builder.Default
    private AiPersona persona = AiPersona.GENZ_DJ;
}
