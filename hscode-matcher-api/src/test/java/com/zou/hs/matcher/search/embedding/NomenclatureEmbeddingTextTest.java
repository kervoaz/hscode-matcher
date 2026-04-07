package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NomenclatureEmbeddingTextTest {

    @Test
    void hs6_includesParentDescription() {
        Language en = Language.EN;
        Map<String, HsEntry> map =
                Map.of(
                        "87", new HsEntry("87", 2, "Vehicles", en, null),
                        "8703", new HsEntry("8703", 4, "Motor cars", en, "87"),
                        "870321",
                                new HsEntry("870321", 6, "Of cylinder capacity <= 1000cc", en, "8703"));
        var reg = new NomenclatureRegistry(en, map);
        HsEntry sub = reg.get("870321").orElseThrow();
        assertThat(NomenclatureEmbeddingText.textForEntry(reg, sub))
                .isEqualTo("Motor cars. Of cylinder capacity <= 1000cc");
    }

    @Test
    void chapter_usesDescriptionOnly() {
        Language en = Language.EN;
        HsEntry ch = new HsEntry("01", 2, "Live animals", en, null);
        var reg = new NomenclatureRegistry(en, Map.of("01", ch));
        assertThat(NomenclatureEmbeddingText.textForEntry(reg, ch)).isEqualTo("Live animals");
    }
}
