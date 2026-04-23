package com.geodis.hs.matcher.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.geodis.hs.matcher.ingestion.RawNomenclatureRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchySearchAugmenterTest {

    @Test
    void hs6LexicalHitAddsChapterAndHeadingRows() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses A"));

        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var lexical =
                List.of(
                        new MatchRow(
                                "010121",
                                6,
                                "Horses A",
                                5.0,
                                "LEXICAL",
                                HierarchyResolver.resolve(reg, reg.get("010121").orElseThrow())));

        List<MatchRow> out = HierarchySearchAugmenter.withParentCategories(reg, lexical);

        assertThat(out).hasSize(3);
        assertThat(out.stream().map(MatchRow::code).toList()).containsExactlyInAnyOrder("01", "0101", "010121");
        assertThat(out.stream().filter(r -> "LEXICAL".equals(r.matchType())).map(MatchRow::code))
                .containsExactly("010121");
        assertThat(out.stream().filter(r -> HierarchySearchAugmenter.PARENT_CONTEXT.equals(r.matchType()))
                        .map(MatchRow::code))
                .containsExactlyInAnyOrder("01", "0101");
    }

    @Test
    void headingLexicalHitAddsOnlyChapter() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"));

        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var lexical =
                List.of(
                        new MatchRow(
                                "0101",
                                4,
                                "Live horses",
                                3.0,
                                "LEXICAL",
                                HierarchyResolver.resolve(reg, reg.get("0101").orElseThrow())));

        List<MatchRow> out = HierarchySearchAugmenter.withParentCategories(reg, lexical);

        assertThat(out).hasSize(2);
        assertThat(out.stream().filter(r -> HierarchySearchAugmenter.PARENT_CONTEXT.equals(r.matchType()))
                        .map(MatchRow::code))
                .containsExactly("01");
    }

    @Test
    void doesNotDuplicateWhenParentAlsoLexicalHit() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses A"));

        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var lexical =
                List.of(
                        new MatchRow(
                                "0101",
                                4,
                                "Live horses",
                                4.0,
                                "LEXICAL",
                                HierarchyResolver.resolve(reg, reg.get("0101").orElseThrow())),
                        new MatchRow(
                                "010121",
                                6,
                                "Horses A",
                                2.0,
                                "LEXICAL",
                                HierarchyResolver.resolve(reg, reg.get("010121").orElseThrow())));

        List<MatchRow> out = HierarchySearchAugmenter.withParentCategories(reg, lexical);

        long headingRows = out.stream().filter(r -> "0101".equals(r.code())).count();
        assertThat(headingRows).isEqualTo(1);
        assertThat(out.stream().filter(r -> "01".equals(r.code())).count()).isEqualTo(1);
    }

    @Test
    void parentContextScoreFactor_reducesAncestorScores() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses A"));

        var reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        var lexical =
                List.of(
                        new MatchRow(
                                "010121",
                                6,
                                "Horses A",
                                10.0,
                                "LEXICAL",
                                HierarchyResolver.resolve(reg, reg.get("010121").orElseThrow())));

        List<MatchRow> out = HierarchySearchAugmenter.withParentCategories(reg, lexical, 0.5);

        MatchRow chapterParent =
                out.stream()
                        .filter(r -> "01".equals(r.code()) && HierarchySearchAugmenter.PARENT_CONTEXT.equals(r.matchType()))
                        .findFirst()
                        .orElseThrow();
        assertThat(chapterParent.score()).isEqualTo(5.0);
    }
}
