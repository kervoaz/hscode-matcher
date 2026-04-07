package com.zou.hs.matcher.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.zou.hs.matcher.ingestion.RawNomenclatureRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchyResolverTest {

    @Test
    void subheadingIncludesChapterHeadingAndPeers() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses A"),
                        new RawNomenclatureRow("0101290000", 6, Language.EN, "Horses B"));

        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var sub = reg.get("010121").orElseThrow();
        var h = HierarchyResolver.resolve(reg, sub);

        assertThat(h.chapter()).isNotNull().extracting(c -> c.code()).isEqualTo("01");
        assertThat(h.heading()).isNotNull().extracting(x -> x.code()).isEqualTo("0101");
        assertThat(h.siblingSubheadings()).hasSize(1);
        assertThat(h.siblingSubheadings().get(0).code()).isEqualTo("010129");
    }

    @Test
    void chapterLevelHasNoHeading() {
        List<RawNomenclatureRow> rows =
                List.of(new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"));
        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var ch = reg.get("01").orElseThrow();
        var h = HierarchyResolver.resolve(reg, ch);
        assertThat(h.chapter().code()).isEqualTo("01");
        assertThat(h.heading()).isNull();
        assertThat(h.siblingSubheadings()).isEmpty();
    }
}
