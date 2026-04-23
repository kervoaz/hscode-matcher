package com.geodis.hs.matcher.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterClassifyResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Optionally persists bulk chapter-classify results to disk when {@code bulk.chapter-classify.output-dir}
 * is set (absolute or relative to the JVM working directory).
 */
@Service
public class BulkChapterOutputService {

    private static final Logger log = LoggerFactory.getLogger(BulkChapterOutputService.class);

    private final String outputDirRaw;
    private final ObjectMapper objectMapper;

    public BulkChapterOutputService(
            @Value("${bulk.chapter-classify.output-dir:}") String outputDirRaw, ObjectMapper objectMapper) {
        this.outputDirRaw = outputDirRaw;
        this.objectMapper = objectMapper;
    }

    /** Writes UTF-8 CSV bytes to {@code chapter-classify-&lt;runId&gt;.csv} when output dir is configured. */
    public Optional<Path> writeCsvIfConfigured(String runId, byte[] csvBytes) throws IOException {
        Optional<Path> dir = resolveOutputDir();
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        Path file = dir.get().resolve("chapter-classify-" + runId + ".csv");
        Files.write(file, csvBytes);
        log.info("bulk chapter CSV result written to {}", file.toAbsolutePath());
        return Optional.of(file.toAbsolutePath());
    }

    /**
     * Writes pretty-printed JSON to {@code chapter-classify-&lt;runId&gt;.json} when output dir is configured.
     * The serialized payload is {@code response} as-is (typically {@code outputFile} is still null).
     */
    public Optional<Path> writeJsonIfConfigured(String runId, BulkChapterClassifyResponse response)
            throws IOException {
        Optional<Path> dir = resolveOutputDir();
        if (dir.isEmpty()) {
            return Optional.empty();
        }
        Path file = dir.get().resolve("chapter-classify-" + runId + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), response);
        log.info("bulk chapter JSON result written to {}", file.toAbsolutePath());
        return Optional.of(file.toAbsolutePath());
    }

    private Optional<Path> resolveOutputDir() throws IOException {
        if (outputDirRaw == null || outputDirRaw.isBlank()) {
            return Optional.empty();
        }
        Path dir = Paths.get(outputDirRaw.trim()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return Optional.of(dir);
    }
}
