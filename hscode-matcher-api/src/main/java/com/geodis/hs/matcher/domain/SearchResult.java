package com.geodis.hs.matcher.domain;

import java.util.Objects;

/** Ranked search outcome with lexical, semantic, and fused scores. */
public record SearchResult(
        HsEntry entry,
        double hybridScore,
        double bm25Score,
        double cosineScore,
        HierarchyContext hierarchy) {

    public SearchResult {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(hierarchy, "hierarchy");
    }
}
