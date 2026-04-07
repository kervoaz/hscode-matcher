package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.search.model.ScoredHit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EmbeddingMatrixCacheTest {

    @Test
    void roundTrip_invalidatesWhenCsvMtimeChanges(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("n.csv");
        Files.writeString(csv, "dummy");
        Path cacheFile = tmp.resolve("EN.embedding-cache");
        String salt = "t";

        float[][] m = {{1f, 0f}, {0f, 1f}};
        String[] codes = {"01", "02"};
        LanguageEmbeddingIndex idx = LanguageEmbeddingIndex.fromPacked(m, codes);
        EmbeddingMatrixCache.save(cacheFile, csv, idx, salt);

        Optional<LanguageEmbeddingIndex> loaded =
                EmbeddingMatrixCache.tryLoad(cacheFile, csv, 2, salt);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().size()).isEqualTo(2);

        Files.writeString(csv, "changed");
        assertThat(EmbeddingMatrixCache.tryLoad(cacheFile, csv, 2, salt)).isEmpty();
    }

    @Test
    void tryLoad_rejectsWrongRowCount(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("n.csv");
        Files.writeString(csv, "x");
        Path cacheFile = tmp.resolve("FR.embedding-cache");
        LanguageEmbeddingIndex idx = LanguageEmbeddingIndex.fromPacked(new float[][] {{1f}}, new String[] {"1"});
        EmbeddingMatrixCache.save(cacheFile, csv, idx, "s");
        assertThat(EmbeddingMatrixCache.tryLoad(cacheFile, csv, 99, "s")).isEmpty();
    }

    @Test
    void searchWorksOnLoadedIndex(@TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("n.csv");
        Files.writeString(csv, "x");
        Path cacheFile = tmp.resolve("DE.embedding-cache");
        float[][] m = {{1f, 0f, 0f}, {0f, 1f, 0f}};
        String[] codes = {"a", "b"};
        EmbeddingMatrixCache.save(cacheFile, csv, LanguageEmbeddingIndex.fromPacked(m, codes), "s");
        LanguageEmbeddingIndex idx =
                EmbeddingMatrixCache.tryLoad(cacheFile, csv, 2, "s").orElseThrow();
        List<ScoredHit> hits = idx.search(new float[] {0f, 1f, 0f}, 1);
        assertThat(hits.get(0).hsCode()).isEqualTo("b");
    }
}
