package com.geodis.hs.matcher.classify;

/** One HS chapter (level 2) candidate with an aggregated lexical score. */
public record ChapterCandidate(String code, String description, double score) {}
