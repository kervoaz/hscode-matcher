package com.geodis.hs.matcher.api.dto;

/**
 * Direct lookup of one nomenclature line by registry key (chapter, heading, HS6, CN8, or CN10).
 */
public record CodeLookupResponse(
        String code, int level, String description, String lang, MatchHierarchy hierarchy) {}
