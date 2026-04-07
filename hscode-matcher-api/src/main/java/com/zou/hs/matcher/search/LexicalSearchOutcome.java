package com.zou.hs.matcher.search;

import java.util.List;

/** Lexical search hits plus metadata showing whether fuzzy token clauses were applied. */
public record LexicalSearchOutcome(List<LexicalHit> hits, int fuzzyTerms) {

    public LexicalSearchOutcome {
        hits = List.copyOf(hits);
    }

    public boolean fuzzyEnabled() {
        return fuzzyTerms > 0;
    }
}
