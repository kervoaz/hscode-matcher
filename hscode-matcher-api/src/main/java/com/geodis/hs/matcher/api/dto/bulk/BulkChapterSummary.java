package com.geodis.hs.matcher.api.dto.bulk;

/** Aggregate stats for a bulk chapter-classification run (Lucene path; LLM columns reserved). */
public record BulkChapterSummary(
        int itemCount,
        int luceneChapterPresentCount,
        int luceneAmbiguousCount,
        int luceneErrorOrEmptyCount,
        double avgConfidenceLucene) {}
