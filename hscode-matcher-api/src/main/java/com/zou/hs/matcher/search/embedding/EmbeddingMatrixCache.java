package com.zou.hs.matcher.search.embedding;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists {@link LanguageEmbeddingIndex} binary snapshots on disk. Invalidated when the source CSV
 * size/mtime, row count, cache salt, or absolute path string changes.
 */
public final class EmbeddingMatrixCache {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingMatrixCache.class);
    private static final int MAGIC = 0x454d_4249; // "EMBI"
    private static final int VERSION = 1;

    private EmbeddingMatrixCache() {}

    public static Optional<LanguageEmbeddingIndex> tryLoad(
            Path cacheFile,
            Path csvPath,
            int expectedRows,
            String cacheSalt)
            throws IOException {
        if (!Files.isRegularFile(cacheFile)) {
            return Optional.empty();
        }
        long csvSize = Files.size(csvPath);
        long csvMtime = Files.getLastModifiedTime(csvPath).toMillis();
        String csvPathKey = csvPath.toAbsolutePath().normalize().toString();
        try (InputStream in = Files.newInputStream(cacheFile);
                DataInputStream d = new DataInputStream(in)) {
            if (d.readInt() != MAGIC || d.readInt() != VERSION) {
                log.debug("Embedding cache bad header: {}", cacheFile);
                return Optional.empty();
            }
            String salt = readUtf(d);
            int n = d.readInt();
            int dim = d.readInt();
            long storedMtime = d.readLong();
            long storedSize = d.readLong();
            String storedPath = readUtf(d);
            if (!salt.equals(cacheSalt)
                    || n != expectedRows
                    || storedSize != csvSize
                    || storedMtime != csvMtime
                    || !storedPath.equals(csvPathKey)) {
                log.info(
                        "Embedding cache miss or stale for {} (csv changed or salt/rows differ)",
                        cacheFile.getFileName());
                return Optional.empty();
            }
            String[] codes = new String[n];
            for (int i = 0; i < n; i++) {
                codes[i] = readUtf(d);
            }
            float[][] matrix = new float[n][];
            for (int i = 0; i < n; i++) {
                float[] row = new float[dim];
                for (int j = 0; j < dim; j++) {
                    row[j] = d.readFloat();
                }
                matrix[i] = row;
            }
            log.info("Loaded embedding cache {} ({} x {} vectors)", cacheFile.getFileName(), n, dim);
            return Optional.of(LanguageEmbeddingIndex.fromPacked(matrix, codes));
        }
    }

    public static void save(
            Path cacheFile,
            Path csvPath,
            LanguageEmbeddingIndex index,
            String cacheSalt)
            throws IOException {
        long csvSize = Files.size(csvPath);
        long csvMtime = Files.getLastModifiedTime(csvPath).toMillis();
        String csvPathKey = csvPath.toAbsolutePath().normalize().toString();
        Path parent = cacheFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = cacheFile.resolveSibling(cacheFile.getFileName().toString() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp);
                DataOutputStream d = new DataOutputStream(out)) {
            d.writeInt(MAGIC);
            d.writeInt(VERSION);
            writeUtf(d, cacheSalt);
            d.writeInt(index.size());
            String[] codes = index.codesArray();
            float[][] matrix = index.matrixArray();
            int dim = matrix.length > 0 ? matrix[0].length : 0;
            d.writeInt(dim);
            d.writeLong(csvMtime);
            d.writeLong(csvSize);
            writeUtf(d, csvPathKey);
            for (String c : codes) {
                writeUtf(d, c);
            }
            for (float[] row : matrix) {
                if (row.length != dim) {
                    throw new IOException("Ragged embedding row in index");
                }
                for (float v : row) {
                    d.writeFloat(v);
                }
            }
            d.flush();
        }
        try {
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("Wrote embedding cache {}", cacheFile.getFileName());
    }

    private static String readUtf(DataInputStream d) throws IOException {
        int len = d.readInt();
        byte[] b = new byte[len];
        d.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void writeUtf(DataOutputStream d, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        d.writeInt(b.length);
        d.write(b);
    }
}
