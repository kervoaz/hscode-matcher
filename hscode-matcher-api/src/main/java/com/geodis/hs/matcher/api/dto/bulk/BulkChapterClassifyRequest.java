package com.geodis.hs.matcher.api.dto.bulk;

import java.util.List;

/** JSON body for {@code POST /api/v1/bulk/chapter-classify}. */
public record BulkChapterClassifyRequest(
        String lang,
        List<BulkChapterItemRequest> items,
        Integer searchLimit,
        Boolean fuzzy,
        Boolean bm25,
        Float fuzzyBoost,
        Integer minFuzzyTokenLength,
        Integer fuzzyMaxExpansions,
        Integer fuzzyPrefixLength,
        Integer maxEditsShort,
        Integer maxEditsLong,
        Integer maxQueryChars) {

    public BulkChapterClassifyRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
