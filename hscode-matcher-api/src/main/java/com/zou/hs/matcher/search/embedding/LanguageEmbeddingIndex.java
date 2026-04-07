package com.zou.hs.matcher.search.embedding;

import ai.onnxruntime.OrtException;
import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import com.zou.hs.matcher.search.model.ScoredHit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable per-language embedding matrix aligned with one nomenclature snapshot (same lifetime as
 * the Lucene index in {@link com.zou.hs.matcher.config.NomenclatureIndexBundle}).
 */
public final class LanguageEmbeddingIndex {

    private static final Logger log = LoggerFactory.getLogger(LanguageEmbeddingIndex.class);

    private final float[][] matrix;
    private final String[] codes;

    private LanguageEmbeddingIndex(float[][] matrix, String[] codes) {
        this.matrix = matrix;
        this.codes = codes;
    }

    /** Restores from disk cache (same package as {@link EmbeddingMatrixCache}). */
    static LanguageEmbeddingIndex fromPacked(float[][] matrix, String[] codes) {
        if (matrix.length != codes.length) {
            throw new IllegalArgumentException("matrix rows != codes length");
        }
        return new LanguageEmbeddingIndex(matrix, codes);
    }

    String[] codesArray() {
        return codes;
    }

    float[][] matrixArray() {
        return matrix;
    }

    public static LanguageEmbeddingIndex build(NomenclatureRegistry registry, EmbeddingEngine engine)
            throws IOException {
        List<HsEntry> list = new ArrayList<>(registry.entries());
        int n = list.size();
        float[][] matrix = new float[n][];
        String[] codes = new String[n];
        for (int i = 0; i < n; i++) {
            HsEntry e = list.get(i);
            codes[i] = e.code();
            String text = NomenclatureEmbeddingText.textForEntry(registry, e);
            try {
                matrix[i] = engine.embed(text);
            } catch (OrtException ex) {
                throw new IOException("Embedding failed for " + e.code(), ex);
            }
        }
        return new LanguageEmbeddingIndex(matrix, codes);
    }

    /**
     * Uses {@link EmbeddingMatrixCache} when {@code cacheDirectory} is present; otherwise same as
     * {@link #build}.
     */
    public static LanguageEmbeddingIndex loadOrBuild(
            NomenclatureRegistry registry,
            EmbeddingEngine engine,
            Path csvPath,
            Optional<Path> cacheDirectory,
            String cacheSalt,
            Language language)
            throws IOException {
        int rows = registry.size();
        if (cacheDirectory.isPresent()) {
            Path dir = cacheDirectory.get();
            Path cacheFile = dir.resolve(language.name() + ".embedding-cache");
            Optional<LanguageEmbeddingIndex> cached =
                    EmbeddingMatrixCache.tryLoad(cacheFile, csvPath, rows, cacheSalt);
            if (cached.isPresent()) {
                return cached.get();
            }
        }
        LanguageEmbeddingIndex built = build(registry, engine);
        if (cacheDirectory.isPresent()) {
            Path cacheFile = cacheDirectory.get().resolve(language.name() + ".embedding-cache");
            try {
                EmbeddingMatrixCache.save(cacheFile, csvPath, built, cacheSalt);
            } catch (IOException e) {
                log.warn("Failed to write embedding cache {}: {}", cacheFile, e.toString());
            }
        }
        return built;
    }

    public int size() {
        return matrix.length;
    }

    /** Brute-force cosine (dot product on L2-normalized rows) top-k. */
    public List<ScoredHit> search(float[] queryVec, int limit) {
        if (matrix.length == 0 || limit <= 0) {
            return List.of();
        }
        int n = matrix.length;
        float[] scores = new float[n];
        for (int i = 0; i < n; i++) {
            float dot = 0f;
            float[] row = matrix[i];
            for (int d = 0; d < queryVec.length && d < row.length; d++) {
                dot += queryVec[d] * row[d];
            }
            scores[i] = dot;
        }
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, Comparator.comparing((Integer i) -> scores[i]).reversed());
        int resultCount = Math.min(limit, n);
        List<ScoredHit> out = new ArrayList<>(resultCount);
        for (int i = 0; i < resultCount; i++) {
            int idx = indices[i];
            out.add(new ScoredHit(codes[idx], scores[idx]));
        }
        return out;
    }
}
