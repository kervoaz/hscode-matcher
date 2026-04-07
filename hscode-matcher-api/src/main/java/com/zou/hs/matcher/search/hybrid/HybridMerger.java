package com.zou.hs.matcher.search.hybrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal rank fusion (RRF) over two ordered code lists (e.g. lexical BM25 and semantic cosine).
 */
public final class HybridMerger {

    private HybridMerger() {}

    public record CodeRrf(String code, float rawRrf) {}

    /**
     * Standard RRF: for each list, add 1/(k + rank) where rank is 1-based. Codes appearing in both
     * lists accumulate both contributions.
     */
    public static List<CodeRrf> rrfMerge(List<String> lexicalCodes, List<String> semanticCodes, int k) {
        Map<String, Float> acc = new HashMap<>();
        for (int i = 0; i < lexicalCodes.size(); i++) {
            String c = lexicalCodes.get(i);
            int rank = i + 1;
            acc.merge(c, 1f / (k + rank), Float::sum);
        }
        for (int i = 0; i < semanticCodes.size(); i++) {
            String c = semanticCodes.get(i);
            int rank = i + 1;
            acc.merge(c, 1f / (k + rank), Float::sum);
        }
        List<CodeRrf> out = new ArrayList<>(acc.size());
        for (Map.Entry<String, Float> e : acc.entrySet()) {
            out.add(new CodeRrf(e.getKey(), e.getValue()));
        }
        out.sort((a, b) -> Float.compare(b.rawRrf(), a.rawRrf()));
        return out;
    }

    public record FusionExplainRow(String code, int lexicalRank, int semanticRank, float rrfScore) {}

    /**
     * Top {@code topN} RRF rows with 1-based ranks in each source list (0 if missing).
     */
    public static List<FusionExplainRow> fusionExplainRows(
            List<String> lexicalCodes, List<String> semanticCodes, List<CodeRrf> merged, int topN) {
        java.util.Map<String, Integer> lexR = rankMap1Based(lexicalCodes);
        java.util.Map<String, Integer> semR = rankMap1Based(semanticCodes);
        int n = Math.min(Math.max(0, topN), merged.size());
        List<FusionExplainRow> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            CodeRrf row = merged.get(i);
            rows.add(
                    new FusionExplainRow(
                            row.code(),
                            lexR.getOrDefault(row.code(), 0),
                            semR.getOrDefault(row.code(), 0),
                            row.rawRrf()));
        }
        return rows;
    }

    private static java.util.Map<String, Integer> rankMap1Based(List<String> codes) {
        java.util.Map<String, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < codes.size(); i++) {
            m.put(codes.get(i), i + 1);
        }
        return m;
    }
}
