package com.zou.hs.matcher.search;

import com.zou.hs.matcher.api.dto.SearchExplainPayload;

/** Extra diagnostics attached to a search outcome (lab / explain API). */
public record OutcomeMeta(
        SearchExplainPayload explain,
        boolean hybridSkippedShortQuery,
        boolean embeddingFallback,
        boolean embeddingTimedOut,
        boolean fuzzyDisabledByRequest,
        int effectiveMinFuzzyTokenLength,
        int serverMinHybridQueryChars) {

    public static OutcomeMeta empty() {
        return new OutcomeMeta(null, false, false, false, false, 0, 0);
    }
}
