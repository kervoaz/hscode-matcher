package com.geodis.hs.matcher.api.dto;

import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.util.List;

public record SearchResponse(
        String query,
        String language,
        int returned,
        List<MatchRow> results,
        boolean fuzzyEnabled,
        int fuzzyTerms,
        LexicalSearchParams tuning) {}
