package com.zou.hs.matcher.config;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureIntegrityValidator;
import com.zou.hs.matcher.ingestion.NomenclatureRegistryBuilder;
import com.zou.hs.matcher.ingestion.csv.CsvNomenclatureReader;
import com.zou.hs.matcher.search.embedding.EmbeddingEngine;
import com.zou.hs.matcher.search.embedding.LanguageEmbeddingIndex;
import com.zou.hs.matcher.search.lucene.LanguageSearchIndex;
import com.zou.hs.matcher.search.lucene.LuceneAnalyzers;
import com.zou.hs.matcher.search.lucene.LuceneLexicalSearchService;
import com.zou.hs.matcher.search.lucene.LuceneNomenclatureIndexer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One generation of loaded CSVs + Lucene indexes (+ optional per-language embedding matrices when
 * ONNX is enabled). Close after a newer bundle is published so in-flight searches can finish on the
 * old snapshot.
 */
public final class NomenclatureIndexBundle implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureIndexBundle.class);

    private final List<LanguageSearchIndex> indices = new ArrayList<>();
    private final LuceneLexicalSearchService lexicalSearchService;
    private final Map<Language, LanguageEmbeddingIndex> semanticIndexes;

    private NomenclatureIndexBundle(
            List<LanguageSearchIndex> indices,
            LuceneLexicalSearchService lexical,
            Map<Language, LanguageEmbeddingIndex> semanticIndexes) {
        this.indices.addAll(indices);
        this.lexicalSearchService = lexical;
        this.semanticIndexes = Map.copyOf(semanticIndexes);
    }

    public static NomenclatureIndexBundle load(NomenclatureCsvProperties props) throws IOException {
        return load(props, Optional.empty(), Optional.empty(), "1");
    }

    public static NomenclatureIndexBundle load(NomenclatureCsvProperties props, Optional<EmbeddingEngine> embeddingEngine)
            throws IOException {
        return load(props, embeddingEngine, Optional.empty(), "1");
    }

    public static NomenclatureIndexBundle load(
            NomenclatureCsvProperties props,
            Optional<EmbeddingEngine> embeddingEngine,
            Optional<Path> embeddingCacheDirectory,
            String embeddingCacheSalt)
            throws IOException {
        EnumMap<Language, LanguageSearchIndex> map = new EnumMap<>(Language.class);
        EnumMap<Language, Path> csvPaths = new EnumMap<>(Language.class);
        List<LanguageSearchIndex> closeList = new ArrayList<>();
        tryLoad(props.getEn(), Language.EN, map, csvPaths, closeList);
        tryLoad(props.getFr(), Language.FR, map, csvPaths, closeList);
        tryLoad(props.getDe(), Language.DE, map, csvPaths, closeList);
        LuceneLexicalSearchService service = new LuceneLexicalSearchService(map);
        EnumMap<Language, LanguageEmbeddingIndex> sem = new EnumMap<>(Language.class);
        if (map.isEmpty()) {
            log.warn(
                    "No nomenclature CSV loaded; set nomenclature.csv.{en,fr,de} (UTF-8 export). Search will return no results.");
        } else {
            log.info("Lucene lexical indexes ready for languages: {}", map.keySet());
            if (embeddingEngine.isPresent()) {
                EmbeddingEngine eng = embeddingEngine.get();
                String salt =
                        embeddingCacheSalt == null || embeddingCacheSalt.isBlank()
                                ? "1"
                                : embeddingCacheSalt;
                for (Map.Entry<Language, LanguageSearchIndex> e : map.entrySet()) {
                    Path csvPath = csvPaths.get(e.getKey());
                    if (csvPath == null) {
                        continue;
                    }
                    long t0 = System.nanoTime();
                    LanguageEmbeddingIndex idx =
                            LanguageEmbeddingIndex.loadOrBuild(
                                    e.getValue().registry(), eng, csvPath, embeddingCacheDirectory, salt, e.getKey());
                    sem.put(e.getKey(), idx);
                    log.info(
                            "Embedding index ready for {}: {} vectors in {} ms",
                            e.getKey(),
                            idx.size(),
                            (System.nanoTime() - t0) / 1_000_000L);
                }
            }
        }
        return new NomenclatureIndexBundle(closeList, service, sem);
    }

    private static void tryLoad(
            String pathStr,
            Language language,
            EnumMap<Language, LanguageSearchIndex> map,
            EnumMap<Language, Path> csvPaths,
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
        csvPaths.put(language, path);
        closeList.add(index);
        log.info("Loaded nomenclature for {} from {} ({} entries)", language, path, registry.size());
    }

    public LuceneLexicalSearchService lexicalSearchService() {
        return lexicalSearchService;
    }

    public Optional<LanguageEmbeddingIndex> semanticIndex(Language language) {
        return Optional.ofNullable(semanticIndexes.get(language));
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
