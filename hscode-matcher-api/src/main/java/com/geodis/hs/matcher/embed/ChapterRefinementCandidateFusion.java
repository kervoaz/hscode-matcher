package com.geodis.hs.matcher.embed;

import com.geodis.hs.matcher.classify.ChapterCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merges lexical-ranked chapters with semantic nearest chapters via reciprocal rank fusion (RRF). */
public final class ChapterRefinementCandidateFusion {

    private ChapterRefinementCandidateFusion() {}

    /**
     * @param lexicalRanked chapters from Lucene (best first), scores are BM25-derived
     * @param semanticHits chapters from cosine similarity (best first), score is cosine in [0,1]
     * @param rrfK RRF offset constant
     * @param outK number of candidates to return (e.g. 5 for the LLM shortlist)
     */
    public static List<ChapterCandidate> fuseRrf(
            List<ChapterCandidate> lexicalRanked,
            List<ChapterCandidate> semanticHits,
            int rrfK,
            int outK) {
        if (outK <= 0) {
            return List.of();
        }
        if (semanticHits == null || semanticHits.isEmpty()) {
            return takeLexicalOnly(lexicalRanked, outK);
        }
        if (lexicalRanked == null || lexicalRanked.isEmpty()) {
            return semanticHits.stream().limit(outK).toList();
        }
        int k = Math.max(1, rrfK);
        Map<String, Double> rrf = new HashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();

        int rank = 1;
        for (ChapterCandidate c : lexicalRanked) {
            if (c.code() == null || c.code().isBlank()) {
                continue;
            }
            String code = normalizeChapter(c.code());
            rrf.merge(code, 1.0 / (k + rank), Double::sum);
            descriptions.putIfAbsent(code, c.description());
            rank++;
        }
        rank = 1;
        for (ChapterCandidate c : semanticHits) {
            if (c.code() == null || c.code().isBlank()) {
                continue;
            }
            String code = normalizeChapter(c.code());
            rrf.merge(code, 1.0 / (k + rank), Double::sum);
            descriptions.putIfAbsent(code, c.description());
            rank++;
        }
        List<Map.Entry<String, Double>> sorted =
                new ArrayList<>(rrf.entrySet()).stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .toList();
        List<ChapterCandidate> out = new ArrayList<>(Math.min(outK, sorted.size()));
        for (int i = 0; i < sorted.size() && out.size() < outK; i++) {
            Map.Entry<String, Double> e = sorted.get(i);
            out.add(new ChapterCandidate(e.getKey(), descriptions.getOrDefault(e.getKey(), ""), e.getValue()));
        }
        return List.copyOf(out);
    }

    private static List<ChapterCandidate> takeLexicalOnly(List<ChapterCandidate> lexicalRanked, int outK) {
        if (lexicalRanked == null || lexicalRanked.isEmpty()) {
            return List.of();
        }
        return lexicalRanked.stream().limit(outK).toList();
    }

    private static String normalizeChapter(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() >= 2) {
            return digits.substring(0, 2);
        }
        if (digits.length() == 1) {
            return "0" + digits;
        }
        return raw;
    }

    /** Ranks chapters by dot product (cosine when vectors are normalized). */
    public static List<ChapterCandidate> rankBySimilarity(
            float[] query, Map<String, float[]> byChapter, Map<String, String> chapterTitles, int limit) {
        if (query.length == 0 || limit <= 0) {
            return List.of();
        }
        record Sc(String code, double score) {}
        List<Sc> scored = new ArrayList<>();
        for (Map.Entry<String, float[]> e : byChapter.entrySet()) {
            float[] v = e.getValue();
            if (v.length != query.length) {
                continue;
            }
            double dot = 0;
            for (int i = 0; i < query.length; i++) {
                dot += query[i] * v[i];
            }
            scored.add(new Sc(e.getKey(), dot));
        }
        scored.sort(Comparator.comparingDouble(Sc::score).reversed());
        List<ChapterCandidate> out = new ArrayList<>(Math.min(limit, scored.size()));
        for (int i = 0; i < scored.size() && out.size() < limit; i++) {
            Sc s = scored.get(i);
            String title = chapterTitles.getOrDefault(s.code, "");
            out.add(new ChapterCandidate(s.code, title, s.score));
        }
        return out;
    }
}
