package com.geodis.hs.matcher.search.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.geodis.hs.matcher.ingestion.RawNomenclatureRow;
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
}
