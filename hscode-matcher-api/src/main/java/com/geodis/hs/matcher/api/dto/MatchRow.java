package com.geodis.hs.matcher.api.dto;

/**
 * One search row: direct lexical hit ({@code matchType = "LEXICAL"}) or an ancestor line added for
 * context ({@code "PARENT_CONTEXT"}).
 */
public record MatchRow(
        String code,
        int level,
        String description,
        double score,
        String matchType,
        MatchHierarchy hierarchy) {}
