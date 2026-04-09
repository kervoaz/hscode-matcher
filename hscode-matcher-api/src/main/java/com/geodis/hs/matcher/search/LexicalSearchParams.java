package com.geodis.hs.matcher.search;

/**
 * Tunable lexical search knobs (Lucene BM25 + fuzzy). Values are clamped by {@link #normalized()}.
 */
public record LexicalSearchParams(
        boolean fuzzy,
        boolean bm25,
        float fuzzyBoost,
        int minFuzzyTokenLength,
        int fuzzyMaxExpansions,
        int fuzzyPrefixLength,
        int maxEditsShort,
        int maxEditsLong,
        int maxQueryChars) {

    public static final LexicalSearchParams DEFAULT =
            new LexicalSearchParams(true, true, 4.0f, 3, 120, 1, 1, 2, 512);

    private static final int ABS_MAX_QUERY_CHARS = 512;

    /** True when at least one retrieval clause (fuzzy or BM25) is enabled. */
    public boolean isRunnable() {
        return fuzzy || bm25;
    }

    /**
     * Applies safe bounds. {@link #fuzzyBoost} is clamped to {@code [0.25, 32]}; use {@code fuzzy=false}
     * to turn fuzzy off entirely.
     */
    public LexicalSearchParams normalized() {
        float fb = clampFloat(fuzzyBoost, 0.25f, 32f);
        int minTok = clampInt(minFuzzyTokenLength, 1, 20);
        int maxExp = clampInt(fuzzyMaxExpansions, 1, 500);
        int prefix = clampInt(fuzzyPrefixLength, 0, 2);
        int es = clampInt(maxEditsShort, 0, 2);
        int el = clampInt(maxEditsLong, 0, 2);
        int mqc = clampInt(maxQueryChars, 32, ABS_MAX_QUERY_CHARS);
        return new LexicalSearchParams(fuzzy, bm25, fb, minTok, maxExp, prefix, es, el, mqc);
    }

    private static float clampFloat(float v, float lo, float hi) {
        return Math.min(hi, Math.max(lo, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.min(hi, Math.max(lo, v));
    }

    /**
     * Merges optional request overrides with {@link #DEFAULT}, then {@linkplain #normalized() normalizes}.
     */
    public static LexicalSearchParams merge(
            Boolean fuzzy,
            Boolean bm25,
            Float fuzzyBoost,
            Integer minFuzzyTokenLength,
            Integer fuzzyMaxExpansions,
            Integer fuzzyPrefixLength,
            Integer maxEditsShort,
            Integer maxEditsLong,
            Integer maxQueryChars) {
        LexicalSearchParams d = DEFAULT;
        return new LexicalSearchParams(
                        fuzzy != null ? fuzzy : d.fuzzy(),
                        bm25 != null ? bm25 : d.bm25(),
                        fuzzyBoost != null ? fuzzyBoost : d.fuzzyBoost(),
                        minFuzzyTokenLength != null ? minFuzzyTokenLength : d.minFuzzyTokenLength(),
                        fuzzyMaxExpansions != null ? fuzzyMaxExpansions : d.fuzzyMaxExpansions(),
                        fuzzyPrefixLength != null ? fuzzyPrefixLength : d.fuzzyPrefixLength(),
                        maxEditsShort != null ? maxEditsShort : d.maxEditsShort(),
                        maxEditsLong != null ? maxEditsLong : d.maxEditsLong(),
                        maxQueryChars != null ? maxQueryChars : d.maxQueryChars())
                .normalized();
    }
}
