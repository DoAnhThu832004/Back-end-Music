package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.CacheBypassReason;
import com.devteria.identityservice.enums.CacheScope;

public record CacheEvaluationResult(
    CacheScope scope,
    CacheBypassReason bypassReason
) {
    public static CacheEvaluationResult general() {
        return new CacheEvaluationResult(CacheScope.GENERAL, CacheBypassReason.NONE);
    }

    public static CacheEvaluationResult bypass(CacheBypassReason reason) {
        return new CacheEvaluationResult(CacheScope.BYPASS, reason);
    }
}
