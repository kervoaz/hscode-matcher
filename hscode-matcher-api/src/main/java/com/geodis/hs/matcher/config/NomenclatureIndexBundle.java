package com.geodis.hs.matcher.config;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureIntegrityValidator;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.geodis.hs.matcher.ingestion.RawNomenclatureRow;
import com.geodis.hs.matcher.ingestion.csv.CsvNomenclatureReader;
import com.geodis.hs.matcher.search.lucene.LanguageSearchIndex;
import com.geodis.hs.matcher.search.lucene.LuceneAnalyzers;
import com.geodis.hs.matcher.search.lucene.LuceneLexicalSearchService;
import com.geodis.hs.matcher.search.lucene.LuceneNomenclatureIndexer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One generation of loaded CSVs + Lucene indexes. Close after a newer bundle is published so
 * in-flight searches can finish on the old snapshot.
 */
public final class NomenclatureIndexBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureIndexBundle.class);

    private final List<LanguageSearchIndex> indices = new ArrayList<>();
    private final LuceneLexicalSearchService lexicalSearchService;

    private NomenclatureIndexBundle(List<LanguageSearchIndex> indices, LuceneLexicalSearchService lexical) {
        this.indices.addAll(indices);
        this.lexicalSearchService = lexical;
    }

    public static NomenclatureIndexBundle load(NomenclatureCsvProperties props) throws IOException {
        return load(props, null);
    }

    public static NomenclatureIndexBundle load(
            NomenclatureCsvProperties props, LexicalSearchRankProperties ranking) throws IOException {
        EnumMap<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
        List<LanguageSearchIndex> closeList = new ArrayList<>();
        tryLoad(props.getEn(), Language.EN, map, closeList);
        tryLoad(props.getFr(), Language.FR, map, closeList);
        tryLoad(props.getDe(), Language.DE, map, closeList);
        LuceneLexicalSearchService service = new LuceneLexicalSearchService(map, ranking);
        if (map.isEmpty()) {
            log.warn(
                    "No nomenclature CSV loaded; set nomenclature.csv.{en,fr,de} (UTF-8 export). Search will return no results.");
        } else {
            log.info("Lucene lexical indexes ready for languages: {}", map.keySet());
        }
        return new NomenclatureIndexBundle(closeList, service);
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
        List<RawNomenclatureRow> rows;
        if (pathStr.startsWith("classpath:")) {
            String loc = pathStr.substring("classpath:".length()).trim();
            if (loc.startsWith("/")) {
                loc = loc.substring(1);
            }
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) {
                cl = NomenclatureIndexBundle.class.getClassLoader();
            }
            try (InputStream in = cl.getResourceAsStream(loc)) {
                if (in == null) {
                    log.warn("Nomenclature CSV not found on classpath for {}: {}", language, pathStr);
                    return;
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    rows = CsvNomenclatureReader.readAll(reader);
                }
            }
        } else {
            Path path = Path.of(pathStr).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                log.warn("Nomenclature CSV not found for {}: {}", language, path);
                return;
            }
            rows = CsvNomenclatureReader.readAll(path);
        }
        var registry = NomenclatureRegistryBuilder.build(rows, language);
        NomenclatureIntegrityValidator.validate(registry, NomenclatureIntegrityValidator.Expectations.euCircabcFullExport());
        Analyzer analyzer = LuceneAnalyzers.forLanguage(language);
        Directory directory = LuceneNomenclatureIndexer.build(registry, analyzer);
        LanguageSearchIndex index = new LanguageSearchIndex(language, registry, analyzer, directory);
        map.put(language, index);
        closeList.add(index);
        log.info("Loaded nomenclature for {} from {} ({} entries)", language, pathStr, registry.size());
    }

    public LuceneLexicalSearchService lexicalSearchService() {
        return lexicalSearchService;
    }

    public boolean anyLanguageReady() {
        return !indices.isEmpty();
    }

    @Override
    public void close() {
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
