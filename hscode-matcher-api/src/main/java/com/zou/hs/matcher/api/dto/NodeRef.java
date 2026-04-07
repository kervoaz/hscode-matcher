package com.zou.hs.matcher.api.dto;

/** Compact HS node for JSON (chapter, heading, or sibling line). */
public record NodeRef(String code, int level, String description) {}
