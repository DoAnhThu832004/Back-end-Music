package com.devteria.identityservice.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ai.router")
@Getter
@Setter
public class AiRouterProperties {
    private boolean fastPathEnabled = true;
    private boolean reasoningTierEnabled = true;
    private String fastTierModel = "gemini-3.5-flash-lite";
    private String reasoningTierModel = "gemini-2.5-pro";
    private long firstTokenTimeoutMs = 8000;
}
