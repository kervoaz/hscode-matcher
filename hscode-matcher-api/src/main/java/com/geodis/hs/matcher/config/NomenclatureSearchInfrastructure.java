package com.geodis.hs.matcher.config;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureIntegrityValidator;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.geodis.hs.matcher.ingestion.csv.CsvNomenclatureReader;
import com.geodis.hs.matcher.search.lucene.LanguageSearchIndex;
import com.geodis.hs.matcher.search.lucene.LuceneAnalyzers;
import com.geodis.hs.matcher.search.lucene.LuceneLexicalSearchService;
import com.geodis.hs.matcher.search.lucene.LuceneNomenclatureIndexer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/** Loads nomenclature CSVs, validates, builds per-language Lucene indexes; closes resources on shutdown. */
public final class NomenclatureSearchInfrastructure implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureSearchInfrastructure.class);

    private final List<LanguageSearchIndex> indices = new ArrayList<>();
    private final LuceneLexicalSearchService lexicalSearchService;

    private NomenclatureSearchInfrastructure(
            List<LanguageSearchIndex> indices, LuceneLexicalSearchService lexicalSearchService) {
        this.indices.addAll(indices);
        this.lexicalSearchService = lexicalSearchService;
    }

    public static NomenclatureSearchInfrastructure load(NomenclatureCsvProperties props) throws IOException {
        EnumMap<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
        List<LanguageSearchIndex> closeList = new ArrayList<>();
        tryLoad(props.getEn(), Language.EN, map, closeList);
        tryLoad(props.getFr(), Language.FR, map, closeList);
        tryLoad(props.getDe(), Language.DE, map, closeList);
        LuceneLexicalSearchService service = new LuceneLexicalSearchService(map);
        if (map.isEmpty()) {
            log.warn(
                    "No nomenclature CSV loaded; set nomenclature.csv.{en,fr,de} (UTF-8 export). Search will return no results.");
        } else {
            log.info("Lucene lexical indexes ready for languages: {}", map.keySet());
        }
        return new NomenclatureSearchInfrastructure(closeList, service);
    }

    private static void tryLoad(
            String pathStr,
            Language language,
            EnumMap<Language, LanguageSearchIndex> map,
            List<LanguageSearchIndex> closeList)
            throws IOException {
        if (pathStr == null || pathStr.isBlank()) {
            log.debug("Skipping {}; no CSV path configured", language);
            return;
        }
        Path path = Path.of(pathStr).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            log.warn("Nomenclature CSV not found for {}: {}", language, path);
            return;
        }
        var rows = CsvNomenclatureReader.readAll(path);
        var registry = NomenclatureRegistryBuilder.build(rows, language);
        NomenclatureIntegrityValidator.validate(registry, NomenclatureIntegrityValidator.Expectations.euCircabcFullExport());
        Analyzer analyzer = LuceneAnalyzers.forLanguage(language);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(language, registry, analyzer, directory);
        map.put(language, index);
        closeList.add(index);
        log.info("Loaded nomenclature for {} from {} ({} entries)", language, path, registry.size());
    }

    public LuceneLexicalSearchService lexicalSearchService() {
        return lexicalSearchService;
    }

    public boolean anyLanguageReady() {
        return !indices.isEmpty();
    }

    @Override
    public void destroy() throws Exception {
        for (LanguageSearchIndex index : indices) {
            try {
                index.close();
            } catch (IOException e) {
                log.warn("Error closing Lucene index for {}: {}", index.language(), e.toString());
            }
        }
        indices.clear();
    }
}
