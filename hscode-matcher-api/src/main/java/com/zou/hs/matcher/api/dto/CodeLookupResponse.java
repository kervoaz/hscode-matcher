package com.zou.hs.matcher.api.dto;

/**
 * Direct lookup of one nomenclature line by HS key (chapter, heading, or 6-digit subheading).
 */
public record CodeLookupResponse(
        String code, int level, String description, String lang, MatchHierarchy hierarchy) {}
