package com.geodis.hs.matcher.api.dto;

import java.util.List;

public record SearchResponse(
        String query,
        String language,
        int returned,
        List<MatchRow> results,
        boolean fuzzyEnabled,
        int fuzzyTerms) {}
