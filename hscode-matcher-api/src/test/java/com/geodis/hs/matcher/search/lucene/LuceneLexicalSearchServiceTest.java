package com.geodis.hs.matcher.search.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.geodis.hs.matcher.ingestion.RawNomenclatureRow;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;

class LuceneLexicalSearchServiceTest {

    @Test
    void fuzzyToleratesTypoInEnglish() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses, asses and hinnies"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses pure-bred breeding"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.EN);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.EN);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.EN, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.EN, index);
            var service = new LuceneLexicalSearchService(map);

            var out = service.search(Language.EN, "horsses", 5);
            assertThat(out.fuzzyTerms()).isPositive();
            assertThat(out.hits()).isNotEmpty();
            assertThat(out.hits().get(0).entry().code()).matches("0101|010121");
        } finally {
            index.close();
        }
    }

    @Test
    void englishStopWordOtherIsSearchable() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101290000", 6, Language.EN, "Other horses"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.EN);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.EN);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.EN, registry, analyzer, directory);
        try {
            EnumMap<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.EN, index);
            var service = new LuceneLexicalSearchService(map);
            var out = service.search(Language.EN, "other", 5);
            assertThat(out.fuzzyTerms()).isEqualTo(1);
            assertThat(out.hits()).isNotEmpty();
            assertThat(out.hits().get(0).entry().code()).isEqualTo("010129");
        } finally {
            index.close();
        }
    }

    @Test
    void bm25OnlyDoesNotCorrectTypo() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses, asses and hinnies"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses pure-bred breeding"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.EN);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.EN);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.EN, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.EN, index);
            var service = new LuceneLexicalSearchService(map);
            var params =
                    new LexicalSearchParams(false, true, 4.0f, 3, 120, 1, 1, 2, 512).normalized();
            var out = service.search(Language.EN, "horsses", 5, params);
            assertThat(out.fuzzyTerms()).isZero();
            assertThat(out.hits()).isEmpty();
        } finally {
            index.close();
        }
    }

    @Test
    void frenchXeresQueryHitsCn8WhenHs6TextDoesNotContainTerm() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("2204000000", 2, Language.FR, "Chapitre 22"),
                        new RawNomenclatureRow("2204000000", 4, Language.FR, "Vins"),
                        new RawNomenclatureRow("2204290000", 6, Language.FR, "Autres vins de raisin"),
                        new RawNomenclatureRow("2204298600", 8, Language.FR, "Vin de Xérès"),
                        new RawNomenclatureRow("2204298610", 10, Language.FR, "Titre alcoométrique 15 à 18 % vol"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.FR);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.FR);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.FR, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.FR, index);
            var service = new LuceneLexicalSearchService(map);
            var out = service.search(Language.FR, "xeres", 10);
            assertThat(out.hits()).isNotEmpty();
            assertThat(out.hits()).extracting(h -> h.entry().code()).contains("22042986");
            assertThat(out.hits().get(0).entry().level()).isEqualTo(8);
            assertThat(out.hits().get(0).entry().code()).isEqualTo("22042986");
        } finally {
            index.close();
        }
    }

    @Test
    void frenchXeresQueryHitsCn10ViaAncestorIndexedText_descriptionStaysOfficial() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("2204000000", 2, Language.FR, "Chapitre 22"),
                        new RawNomenclatureRow("2204000000", 4, Language.FR, "Vins"),
                        new RawNomenclatureRow("2204290000", 6, Language.FR, "Autres vins de raisin"),
                        new RawNomenclatureRow("2204298600", 8, Language.FR, "Vin de Xérès"),
                        new RawNomenclatureRow("2204298610", 10, Language.FR, "Titre alcoométrique 15 à 18 % vol"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.FR);
        String officialCn10 = registry.get("2204298610").orElseThrow().description();
        assertThat(officialCn10).isEqualTo("Titre alcoométrique 15 à 18 % vol");

        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.FR);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.FR, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.FR, index);
            var service = new LuceneLexicalSearchService(map);
            var out = service.search(Language.FR, "xeres", 10);
            assertThat(out.hits()).extracting(h -> h.entry().code()).contains("2204298610");
            var cn10Hit =
                    out.hits().stream()
                            .filter(h -> h.entry().code().equals("2204298610"))
                            .findFirst()
                            .orElseThrow();
            assertThat(cn10Hit.entry().level()).isEqualTo(10);
            assertThat(cn10Hit.entry().description()).isEqualTo(officialCn10);
        } finally {
            index.close();
        }
    }

    @Test
    void frenchAsciiQueryMatchesAccentedText() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("2205000000", 6, Language.FR, "Vins de Xérès ou de Montilla"),
                        new RawNomenclatureRow("2205100000", 6, Language.FR, "Autre ligne"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.FR);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.FR);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.FR, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.FR, index);
            var service = new LuceneLexicalSearchService(map);
            var out = service.search(Language.FR, "xeres", 5);
            assertThat(out.hits()).isNotEmpty();
            assertThat(out.hits().get(0).entry().code()).isEqualTo("220500");
        } finally {
            index.close();
        }
    }

    @Test
    void fuzzyOnlyStillMatchesTypo() throws Exception {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses, asses and hinnies"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses pure-bred breeding"));

        var registry = NomenclatureRegistryBuilder.build(rows, Language.EN);
        Analyzer analyzer = LuceneAnalyzers.forLanguage(Language.EN);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(Language.EN, registry, analyzer, directory);
        try {
            Map<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
            map.put(Language.EN, index);
            var service = new LuceneLexicalSearchService(map);
            var params =
                    new LexicalSearchParams(true, false, 4.0f, 3, 120, 1, 1, 2, 512).normalized();
            var out = service.search(Language.EN, "horsses", 5, params);
            assertThat(out.hits()).isNotEmpty();
        } finally {
            index.close();
        }
    }
}
