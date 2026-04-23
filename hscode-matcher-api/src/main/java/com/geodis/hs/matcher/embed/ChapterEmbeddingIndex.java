package com.geodis.hs.matcher.embed;

import com.geodis.hs.matcher.classify.ChapterCandidate;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Lazy per-language index: one embedding vector per HS chapter from aggregated nomenclature text.
 * Invalidated on admin reload so vectors match the current CSV snapshot.
 */
@Component
public class ChapterEmbeddingIndex {

    private static final Logger log = LoggerFactory.getLogger(ChapterEmbeddingIndex.class);

    private final BulkEmbeddingProperties properties;
    private final EmbeddingHttpClient embeddingHttpClient;
    private final NomenclatureSearchRuntime searchRuntime;

    private final Map<Language, IndexData> cache = new ConcurrentHashMap<>();
    private final Map<Language, Object> locks = new ConcurrentHashMap<>();

    public ChapterEmbeddingIndex(
            BulkEmbeddingProperties properties,
            EmbeddingHttpClient embeddingHttpClient,
            NomenclatureSearchRuntime searchRuntime) {
        this.properties = properties;
        this.embeddingHttpClient = embeddingHttpClient;
        this.searchRuntime = searchRuntime;
    }

    public void invalidate() {
        cache.clear();
        embeddingHttpClient.clearQueryCache();
        log.info("Chapter embedding index cleared (nomenclature reload or manual)");
    }

    public boolean isReady(Language language) {
        IndexData d = cache.get(language);
        return d != null && !d.vectorsByChapter().isEmpty();
    }

    /**
     * Returns cosine-ranked chapters, or empty if embeddings are disabled, index missing, or HTTP
     * failure.
     */
    public List<ChapterCandidate> semanticNeighbors(Language language, float[] queryVector, int limit) {
        if (!properties.isEnabled() || queryVector.length == 0) {
            return List.of();
        }
        IndexData data = cache.get(language);
        if (data == null || data.vectorsByChapter().isEmpty()) {
            return List.of();
        }
        return ChapterRefinementCandidateFusion.rankBySimilarity(
                queryVector, data.vectorsByChapter(), data.titlesByChapter(), limit);
    }

    /** Blocking: builds vectors for {@code language} if absent and embeddings are enabled. */
    public void ensureBuilt(Language language) {
        if (!properties.isEnabled()) {
            return;
        }
        if (cache.containsKey(language)) {
            return;
        }
        Object lock = locks.computeIfAbsent(language, l -> new Object());
        synchronized (lock) {
            if (cache.containsKey(language)) {
                return;
            }
            NomenclatureRegistry reg = searchRuntime.registry(language).orElse(null);
            if (reg == null) {
                return;
            }
            Map<String, String> portraits = ChapterPortraitTextBuilder.chapterPortraits(reg);
            if (portraits.isEmpty()) {
                return;
            }
            Map<String, float[]> vectors = new HashMap<>();
            Map<String, String> titles = new HashMap<>();
            for (HsEntryLite e : listChapterMetas(reg)) {
                titles.put(e.code(), e.title());
            }
            long t0 = System.nanoTime();
            for (Map.Entry<String, String> pe : portraits.entrySet()) {
                String ch = pe.getKey();
                float[] v = embeddingHttpClient.embedOrEmpty(pe.getValue());
                if (v.length > 0) {
                    vectors.put(ch, v);
                }
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            cache.put(language, new IndexData(Collections.unmodifiableMap(vectors), Collections.unmodifiableMap(titles)));
            log.info(
                    "Chapter embedding index built for {}: {} chapters in {} ms (model={})",
                    language,
                    vectors.size(),
                    ms,
                    properties.getModel());
        }
    }

    private static List<HsEntryLite> listChapterMetas(NomenclatureRegistry reg) {
        return reg.entries().stream()
                .filter(e -> e.level() == 2 && e.code().length() >= 2)
                .map(e -> new HsEntryLite(e.code().substring(0, 2), e.description()))
                .toList();
    }

    private record HsEntryLite(String code, String title) {}

    private record IndexData(Map<String, float[]> vectorsByChapter, Map<String, String> titlesByChapter) {}

    /** Optional eager init hook. */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.isEnabled() || !properties.isEagerInit()) {
            return;
        }
        for (Language lang : Language.values()) {
            if (searchRuntime.isReady(lang)) {
                try {
                    ensureBuilt(lang);
                } catch (Exception e) {
                    log.warn("Eager chapter embedding init failed for {}: {}", lang, e.getMessage());
                }
            }
        }
    }
}
