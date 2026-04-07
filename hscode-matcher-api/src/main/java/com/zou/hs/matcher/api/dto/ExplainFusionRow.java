package com.zou.hs.matcher.api.dto;

/** After RRF: 0 = code absent from that ranked list. */
public record ExplainFusionRow(String code, int lexicalRank, int semanticRank, float rrfScore) {}
