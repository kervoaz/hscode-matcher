package com.zou.hs.matcher.api.dto;

public record SearchDebugPayload(
        boolean hybridSkippedShortQuery,
        boolean embeddingFallback,
        boolean embeddingTimedOut,
        boolean fuzzyDisabledByRequest,
        int effectiveMinFuzzyTokenLength,
        int serverMinHybridQueryChars) {}
