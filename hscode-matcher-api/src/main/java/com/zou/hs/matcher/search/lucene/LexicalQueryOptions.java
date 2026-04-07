package com.zou.hs.matcher.search.lucene;

/** Per-request lexical tuning (fuzzy branch of the Lucene query). */
public record LexicalQueryOptions(boolean fuzzyEnabled, int minFuzzyTokenLengthOverride) {

    public static final int DEFAULT_MIN_FUZZY_TOKEN_LENGTH = 3;

    public static LexicalQueryOptions defaults() {
        return new LexicalQueryOptions(true, 0);
    }

    public LexicalQueryOptions {
        if (minFuzzyTokenLengthOverride < 0) {
            throw new IllegalArgumentException("minFuzzyTokenLengthOverride must be >= 0");
        }
    }

    /** Effective minimum token length for fuzzy clauses (2–32); 0 override → default 3. */
    public int effectiveMinFuzzyTokenLength() {
        if (minFuzzyTokenLengthOverride <= 0) {
            return DEFAULT_MIN_FUZZY_TOKEN_LENGTH;
        }
        return Math.min(32, Math.max(2, minFuzzyTokenLengthOverride));
    }
}
