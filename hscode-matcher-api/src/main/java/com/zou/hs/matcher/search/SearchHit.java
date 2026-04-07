package com.zou.hs.matcher.search;

import com.zou.hs.matcher.domain.HsEntry;

/** Single fused or lexical hit for the HTTP layer. */
public record SearchHit(HsEntry entry, float score, String matchType) {}
