package com.zou.hs.matcher.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        String query,
        String language,
        int returned,
        List<MatchRow> results,
        boolean fuzzyEnabled,
        int fuzzyTerms,
        boolean hybridEnabled,
        int candidatePool,
        boolean hybridSuppressedByRequest,
        int effectiveRrfK,
        SearchExplainPayload explain,
        SearchDebugPayload debug) {}
