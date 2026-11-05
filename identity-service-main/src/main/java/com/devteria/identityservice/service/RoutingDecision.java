package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.ModelTier;

public record RoutingDecision(
    ModelTier tier,
    String targetModel,
    boolean degradedByBreaker,      // true nếu bị ép sang Flash do Breaker OPEN (Chặn cache)
    boolean reasoningTierDisabled   // true nếu do cấu hình admin tắt (Không chặn cache)
) {}
