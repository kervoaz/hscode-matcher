package com.geodis.hs.matcher.embed;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.classify.ChapterCandidate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChapterRefinementCandidateFusionTest {

    @Test
    void fuseRrf_prefersChapterStrongInBothLists() {
        List<ChapterCandidate> lex =
                List.of(
                        new ChapterCandidate("84", "A", 10),
                        new ChapterCandidate("62", "B", 8),
                        new ChapterCandidate("39", "C", 3));
        // No 84 in semantic top-3 so lexical #1 loses RRF mass vs 62 present in both.
        List<ChapterCandidate> sem =
                List.of(
                        new ChapterCandidate("62", "B", 0.9),
                        new ChapterCandidate("01", "D", 0.5),
                        new ChapterCandidate("02", "E", 0.4));
        List<ChapterCandidate> out = ChapterRefinementCandidateFusion.fuseRrf(lex, sem, 30, 3);
        assertThat(out).hasSize(3);
        assertThat(out.get(0).code()).isEqualTo("62");
    }

    @Test
    void rankBySimilarity_ordersByDotProduct() {
        float[] q = new float[] {1f, 0f};
        Map<String, float[]> by =
                Map.of(
                        "10", new float[] {1f, 0f},
                        "20", new float[] {0f, 1f},
                        "30", new float[] {0.9f, 0.1f});
        Map<String, String> titles = Map.of("10", "T10", "20", "T20", "30", "T30");
        List<ChapterCandidate> r =
                ChapterRefinementCandidateFusion.rankBySimilarity(q, by, titles, 2);
        assertThat(r).hasSize(2);
        assertThat(r.get(0).code()).isEqualTo("10");
        assertThat(r.get(1).code()).isEqualTo("30");
    }
}
