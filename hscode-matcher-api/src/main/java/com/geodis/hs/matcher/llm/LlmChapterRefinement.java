package com.geodis.hs.matcher.llm;

/** Parsed model output for HS chapter (2-digit) refinement. */
public record LlmChapterRefinement(
        String chapter,
        double confidence01,
        boolean ambiguous,
        String rationale,
        /** Non-null if the HTTP or JSON parse failed (then {@code chapter} may be null). */
        String error) {

    public static LlmChapterRefinement error(String message) {
        return new LlmChapterRefinement(null, 0.0, true, null, message);
    }
}
