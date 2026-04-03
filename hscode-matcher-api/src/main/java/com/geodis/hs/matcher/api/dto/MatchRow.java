package com.geodis.hs.matcher.api.dto;

/** One search hit returned to API clients (HS6-centric codes) with parent hierarchy. */
public record MatchRow(
        String code,
        int level,
        String description,
        double score,
        String matchType,
        MatchHierarchy hierarchy) {}
