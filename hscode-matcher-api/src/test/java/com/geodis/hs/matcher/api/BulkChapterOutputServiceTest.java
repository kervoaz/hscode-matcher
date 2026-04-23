package com.geodis.hs.matcher.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterClassifyResponse;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterSummary;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BulkChapterOutputServiceTest {

    @Test
    void writesCsvAndJsonWhenDirConfigured(@TempDir Path temp) throws Exception {
        ObjectMapper om = new ObjectMapper();
        BulkChapterOutputService svc = new BulkChapterOutputService(temp.toString(), om);

        Optional<Path> csv =
                svc.writeCsvIfConfigured(
                        "run-1", "a;b\n1;2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(csv).isPresent();
        assertThat(Files.readString(csv.get())).contains("a;b");

        BulkChapterClassifyResponse resp =
                new BulkChapterClassifyResponse(
                        "run-1",
                        "FR",
                        "pipe",
                        LexicalSearchParams.DEFAULT,
                        List.of(),
                        new BulkChapterSummary(0, 0, 0, 0, 0.0),
                        null);
        Optional<Path> json = svc.writeJsonIfConfigured("run-1", resp);
        assertThat(json).isPresent();
        assertThat(Files.readString(json.get())).contains("\"runId\"");
        assertThat(json.get().getFileName().toString()).isEqualTo("chapter-classify-run-1.json");
    }

    @Test
    void skipsWhenDirBlank() throws Exception {
        BulkChapterOutputService svc = new BulkChapterOutputService("  ", new ObjectMapper());
        assertThat(svc.writeCsvIfConfigured("r", new byte[] {1})).isEmpty();
        assertThat(
                        svc.writeJsonIfConfigured(
                                "r",
                                new BulkChapterClassifyResponse(
                                        "r",
                                        "FR",
                                        "p",
                                        LexicalSearchParams.DEFAULT,
                                        List.of(),
                                        new BulkChapterSummary(0, 0, 0, 0, 0.0),
                                        null)))
                .isEmpty();
    }
}
