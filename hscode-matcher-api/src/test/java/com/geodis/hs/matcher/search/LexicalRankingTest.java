package com.geodis.hs.matcher.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.config.LexicalSearchRankProperties;
import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.domain.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalRankingTest {

    @Test
    void depthMultiplier_increasesWithLevel() {
        var p = new LexicalSearchRankProperties();
        p.setLexicalDepthBoostPerTier(0.1);
        assertThat(LexicalRanking.depthMultiplier(2, p)).isEqualTo(1.0f);
        assertThat(LexicalRanking.depthMultiplier(4, p)).isEqualTo(1.1f);
        assertThat(LexicalRanking.depthMultiplier(10, p)).isEqualTo(1.4f);
    }

    @Test
    void rerankByDepth_prefersDeeperLineWhenBaseScoresEqual() {
        var p = new LexicalSearchRankProperties();
        p.setLexicalDepthBoostPerTier(0.5);
        HsEntry ch = new HsEntry("6200000000", 2, "Chapitre", Language.FR, null);
        HsEntry hs6 = new HsEntry("6203310000", 6, "Foo", Language.FR, "6201000000");
        var raw =
                List.of(new LexicalHit(ch, 10f), new LexicalHit(hs6, 10f));
        var out = LexicalRanking.rerankByDepth(raw, 2, p);
        assertThat(out.get(0).entry().level()).isEqualTo(6);
        assertThat(out.get(1).entry().level()).isEqualTo(2);
    }
}
