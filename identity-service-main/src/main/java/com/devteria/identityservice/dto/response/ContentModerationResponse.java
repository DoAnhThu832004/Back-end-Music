package com.devteria.identityservice.dto.response;

import com.devteria.identityservice.enums.ModerationAction;
import com.devteria.identityservice.enums.ModerationFlag;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentModerationResponse {
    private ModerationFlag flag;
    private ModerationAction suggestedAction;
    private Double confidence;
    private String reason;
}
