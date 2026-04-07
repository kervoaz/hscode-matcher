package com.zou.hs.matcher.api.dto;

import java.util.List;

/** Lexical / semantic ranks and RRF fusion (when {@code explain=true} on search). */
public record SearchExplainPayload(
        List<ExplainLexicalRow> lexical,
        List<ExplainSemanticRow> semantic,
        List<ExplainFusionRow> fusion) {

    public SearchExplainPayload {
        lexical = lexical == null ? List.of() : List.copyOf(lexical);
        semantic = semantic == null ? List.of() : List.copyOf(semantic);
        fusion = fusion == null ? List.of() : List.copyOf(fusion);
    }
}
