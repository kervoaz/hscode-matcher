package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.search.model.ScoredHit;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingSearcherTest {

    @Test
    void search_emptyStore_returnsEmpty() {
        EmbeddingStore store = new EmbeddingStore();
        EmbeddingSearcher searcher = new EmbeddingSearcher(store);
        List<ScoredHit> hits = searcher.search(new float[] {1f, 0f}, Language.EN, 5);
        assertThat(hits).isEmpty();
    }

    @Test
    void search_sortedByScoreDesc() {
        EmbeddingStore store = new EmbeddingStore();
        // Rows are L2-normalized; query aligns with second row → highest score on B
        float[][] matrix = {
            {1f, 0f, 0f},
            {0f, 1f, 0f},
            {0f, 0f, 1f}
        };
        String[] codes = {"A", "B", "C"};
        store.initialize(Language.EN, matrix, codes);
        EmbeddingSearcher searcher = new EmbeddingSearcher(store);
        float[] q = new float[] {0f, 1f, 0f};
        List<ScoredHit> hits = searcher.search(q, Language.EN, 3);
        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).hsCode()).isEqualTo("B");
        assertThat(hits.get(0).score()).isEqualTo(1f);
        assertThat(hits.get(1).score()).isEqualTo(0f);
    }

    @Test
    void search_mismatchedCodesLength_returnsEmpty() {
        EmbeddingStore store = new EmbeddingStore();
        store.initialize(Language.EN, new float[][] {{1f}}, new String[] {"A", "B"});
        EmbeddingSearcher searcher = new EmbeddingSearcher(store);
        assertThat(searcher.search(new float[] {1f}, Language.EN, 5)).isEmpty();
    }
}
