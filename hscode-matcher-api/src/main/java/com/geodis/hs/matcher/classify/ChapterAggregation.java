package com.geodis.hs.matcher.classify;



import java.util.List;



/**

 * Aggregated chapter (HS level 2) from lexical hits. {@link #confidence01()} is the top score’s

 * share of the top-3 score mass (not a calibrated probability).

 *

 * <p>{@link #refinementCandidates()} is the shortlist for the LLM (Lucene top-K, optionally merged

 * with semantic RRF). {@link #lexicalRankedMax10()} feeds fusion and is not exposed on the bulk

 * HTTP DTO.

 */

public record ChapterAggregation(

        String bestChapterCode,

        String bestChapterDescription,

        double confidence01,

        List<ChapterCandidate> top3,

        boolean ambiguous,

        boolean lowInformation,

        String errorCode,

        List<ChapterCandidate> refinementCandidates,

        List<ChapterCandidate> lexicalRankedMax10) {



    public static final String ERR_EMPTY_QUERY = "EMPTY_QUERY";

    public static final String ERR_NO_HITS = "NO_HITS";



    public ChapterAggregation {

        top3 = top3 == null ? List.of() : List.copyOf(top3);

        refinementCandidates = refinementCandidates == null ? List.of() : List.copyOf(refinementCandidates);

        lexicalRankedMax10 = lexicalRankedMax10 == null ? List.of() : List.copyOf(lexicalRankedMax10);

    }



    public ChapterAggregation withRefinementCandidates(List<ChapterCandidate> next) {

        return new ChapterAggregation(

                bestChapterCode,

                bestChapterDescription,

                confidence01,

                top3,

                ambiguous,

                lowInformation,

                errorCode,

                next,

                lexicalRankedMax10);

    }

}

