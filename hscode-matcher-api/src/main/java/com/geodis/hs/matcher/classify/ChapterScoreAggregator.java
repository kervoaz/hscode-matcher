package com.geodis.hs.matcher.classify;

import com.geodis.hs.matcher.api.dto.MatchHierarchy;
import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.api.dto.NodeRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Lucene {@link MatchRow} hits to HS chapters (level 2), using only {@code LEXICAL} rows.
 */
public final class ChapterScoreAggregator {

    /** Second score / first ≥ this ⇒ {@link ChapterAggregation#ambiguous()}. */
    private static final double AMBIGUITY_RATIO_THRESHOLD = 0.58;

    /** Trimmed query length ≤ this ⇒ {@link ChapterAggregation#lowInformation()} and ambiguous. */
    private static final int LOW_INFORMATION_MAX_LEN = 3;

    private ChapterScoreAggregator() {}

    public static ChapterAggregation aggregate(List<MatchRow> rows, String queryTrimmed) {
        return aggregate(rows, queryTrimmed, 5, 10);
    }

    /**
     * @param refinementK max chapters kept for LLM shortlist from Lucene alone (before optional
     *     semantic fusion)
     * @param lexicalPool max chapters from Lucene used in RRF when fusion runs
     */
    public static ChapterAggregation aggregate(
            List<MatchRow> rows, String queryTrimmed, int refinementK, int lexicalPool) {
        if (queryTrimmed == null || queryTrimmed.isEmpty()) {
            return new ChapterAggregation(
                    null,
                    null,
                    0.0,
                    List.of(),
                    true,
                    true,
                    ChapterAggregation.ERR_EMPTY_QUERY,
                    List.of(),
                    List.of());
        }
        boolean lowInfo = queryTrimmed.length() <= LOW_INFORMATION_MAX_LEN;
        Map<String, ChapterAccumulator> byChapter = new HashMap<>();
        for (MatchRow row : rows) {
            if (!"LEXICAL".equals(row.matchType())) {
                continue;
            }
            NodeRef ch = chapterRef(row.hierarchy());
            if (ch == null || ch.code() == null || ch.code().isEmpty()) {
                continue;
            }
            String code = ch.code().length() >= 2 ? ch.code().substring(0, 2) : ch.code();
            byChapter.merge(code, new ChapterAccumulator(ch.description(), row.score()), ChapterAccumulator::max);
        }
        if (byChapter.isEmpty()) {
            return new ChapterAggregation(
                    null,
                    null,
                    0.0,
                    List.of(),
                    true,
                    lowInfo,
                    ChapterAggregation.ERR_NO_HITS,
                    List.of(),
                    List.of());
        }
        List<ChapterAccumulator> sorted =
                byChapter.entrySet().stream()
                        .map(e -> e.getValue().withCode(e.getKey()))
                        .sorted(Comparator.comparingDouble(ChapterAccumulator::score).reversed())
                        .toList();
        int rk = Math.max(1, refinementK);
        int lp = Math.max(rk, lexicalPool);
        List<ChapterCandidate> top3 = new ArrayList<>(3);
        double mass = 0.0;
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            ChapterAccumulator a = sorted.get(i);
            top3.add(new ChapterCandidate(a.code(), a.description(), a.score()));
            mass += a.score();
        }
        List<ChapterCandidate> refinement = new ArrayList<>(Math.min(rk, sorted.size()));
        for (int i = 0; i < Math.min(rk, sorted.size()); i++) {
            ChapterAccumulator a = sorted.get(i);
            refinement.add(new ChapterCandidate(a.code(), a.description(), a.score()));
        }
        List<ChapterCandidate> lexicalRanked = new ArrayList<>(Math.min(lp, sorted.size()));
        for (int i = 0; i < Math.min(lp, sorted.size()); i++) {
            ChapterAccumulator a = sorted.get(i);
            lexicalRanked.add(new ChapterCandidate(a.code(), a.description(), a.score()));
        }
        ChapterAccumulator best = sorted.get(0);
        double confidence = mass > 0 ? best.score() / mass : 0.0;
        boolean ambiguous = false;
        if (sorted.size() >= 2) {
            double s1 = sorted.get(0).score();
            double s2 = sorted.get(1).score();
            if (s1 > 0 && s2 / s1 >= AMBIGUITY_RATIO_THRESHOLD) {
                ambiguous = true;
            }
        }
        if (lowInfo) {
            ambiguous = true;
        }
        return new ChapterAggregation(
                best.code(),
                best.description(),
                confidence,
                List.copyOf(top3),
                ambiguous,
                lowInfo,
                null,
                List.copyOf(refinement),
                List.copyOf(lexicalRanked));
    }

    private static NodeRef chapterRef(MatchHierarchy h) {
        if (h == null || h.chapter() == null) {
            return null;
        }
        return h.chapter();
    }

    private record ChapterAccumulator(String code, String description, double score) {
        ChapterAccumulator(String description, double score) {
            this("", description, score);
        }

        ChapterAccumulator withCode(String c) {
            return new ChapterAccumulator(c, description, score);
        }

        static ChapterAccumulator max(ChapterAccumulator a, ChapterAccumulator b) {
            return a.score >= b.score ? a : b;
        }
    }
}
