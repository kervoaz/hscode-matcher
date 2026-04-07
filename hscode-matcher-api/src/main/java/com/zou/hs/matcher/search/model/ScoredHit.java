package com.zou.hs.matcher.search.model;

/** One semantic hit: HS code (or mock id in tests) and cosine similarity when vectors are L2-normalized. */
public record ScoredHit(String hsCode, float score) {}
