package com.devteria.identityservice.enums;

public enum CacheBypassReason {
    NONE,
    EMPTY_PROMPT,
    HISTORY,
    HAS_SONG_CONTEXT,
    PERSONAL,
    PII,
    ANAPHORA,
    TEMPORAL,
    DANGLING_REF
}
