package com.zou.hs.matcher.search;

import java.util.ArrayList;
import java.util.List;

/** Search results after optional lexical + semantic RRF fusion. */
public record UnifiedSearchOutcome(
        List<SearchHit> hits, int fuzzyTerms, boolean hybridEnabled, int candidatePool, OutcomeMeta meta) {

    public UnifiedSearchOutcome {
        hits = List.copyOf(hits);
    }

    public boolean fuzzyEnabled() {
        return fuzzyTerms > 0;
    }

    public static UnifiedSearchOutcome empty() {
        return new UnifiedSearchOutcome(List.of(), 0, false, 0, OutcomeMeta.empty());
    }

    public static UnifiedSearchOutcome fromLexicalOnly(
            LexicalSearchOutcome lex, int limit, int candidatePool, OutcomeMeta meta) {
        List<SearchHit> hits = new ArrayList<>(Math.min(limit, lex.hits().size()));
        int i = 0;
        for (LexicalHit h : lex.hits()) {
            if (i++ >= limit) {
                break;
            }
            hits.add(new SearchHit(h.entry(), h.score(), "LEXICAL"));
        }
        return new UnifiedSearchOutcome(hits, lex.fuzzyTerms(), false, candidatePool, meta);
    }
}
