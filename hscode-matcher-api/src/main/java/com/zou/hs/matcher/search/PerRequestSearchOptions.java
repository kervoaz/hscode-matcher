package com.zou.hs.matcher.search;

/**
 * Per-request overrides for search (lab / UI). Numeric zeros mean "use server default" where
 * documented.
 */
public record PerRequestSearchOptions(
        boolean allowHybrid,
        int rrfKOverride,
        int poolMultiplierOverride,
        boolean fuzzyEnabled,
        int minFuzzyTokenLengthOverride,
        boolean explain,
        int minHybridQueryChars,
        int embedTimeoutMsOverride) {

    public static PerRequestSearchOptions defaults() {
        return new PerRequestSearchOptions(true, 0, 0, true, 0, false, 0, 0);
    }

    public PerRequestSearchOptions {
        if (rrfKOverride < 0) {
            throw new IllegalArgumentException("rrfKOverride must be >= 0");
        }
        if (poolMultiplierOverride < 0) {
            throw new IllegalArgumentException("poolMultiplierOverride must be >= 0");
        }
        if (minFuzzyTokenLengthOverride < 0) {
            throw new IllegalArgumentException("minFuzzyTokenLengthOverride must be >= 0");
        }
        if (minHybridQueryChars < 0) {
            throw new IllegalArgumentException("minHybridQueryChars must be >= 0");
        }
        if (embedTimeoutMsOverride < 0) {
            throw new IllegalArgumentException("embedTimeoutMsOverride must be >= 0");
        }
    }
}
