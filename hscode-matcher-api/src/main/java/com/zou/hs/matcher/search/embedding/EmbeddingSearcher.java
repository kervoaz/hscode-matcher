package com.zou.hs.matcher.search.embedding;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.search.model.ScoredHit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingSearcher {

    private final EmbeddingStore store;

    public EmbeddingSearcher(EmbeddingStore store) {
        this.store = store;
    }

    public List<ScoredHit> search(float[] queryVec, Language lang, int limit) {
        float[][] matrix = store.matrix(lang);
        String[] codes = store.codes(lang);
        if (matrix == null || matrix.length == 0 || codes == null || codes.length != matrix.length) {
            return List.of();
        }
        int n = matrix.length;
        float[] scores = new float[n];
        for (int i = 0; i < n; i++) {
            float dot = 0f;
            float[] row = matrix[i];
            for (int d = 0; d < queryVec.length && d < row.length; d++) {
                dot += queryVec[d] * row[d];
            }
            scores[i] = dot;
        }
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing((Integer i) -> scores[i]).reversed());
        int resultCount = Math.min(limit, n);
        List<ScoredHit> results = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            int idx = indices[i];
            results.add(new ScoredHit(codes[idx], scores[idx]));
        }
        return results;
    }
}
