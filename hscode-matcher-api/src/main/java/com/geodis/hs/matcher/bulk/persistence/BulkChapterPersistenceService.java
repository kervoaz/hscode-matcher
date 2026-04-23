package com.geodis.hs.matcher.bulk.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterItemResult;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists bulk chapter-classify runs and line items to Postgres (e.g. Supabase) using JDBC.
 * Buffers rows and flushes in batches. Idempotent per ({@code run_id}, {@code row_index}).
 */
@Service
@ConditionalOnProperty(prefix = "bulk.persistence", name = "enabled", havingValue = "true")
public class BulkChapterPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(BulkChapterPersistenceService.class);

    private static final String UPSERT_ITEM =
            """
            INSERT INTO bulk_run_item (run_id, row_index, quotation_id, result_payload)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (run_id, row_index) DO UPDATE SET
              quotation_id = EXCLUDED.quotation_id,
              result_payload = EXCLUDED.result_payload,
              created_at = now()
            """;

    private static final String UPSERT_CHUNK =
            """
            INSERT INTO bulk_run_chunk (run_id, chunk_index, status, item_count, finished_at)
            VALUES (?, ?, 'DONE', ?, now())
            ON CONFLICT (run_id, chunk_index) DO UPDATE SET
              status = 'DONE',
              item_count = EXCLUDED.item_count,
              finished_at = EXCLUDED.finished_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public BulkChapterPersistenceService(
            JdbcTemplate bulkJdbcTemplate,
            ObjectMapper objectMapper,
            BulkPersistenceProperties properties) {
        this.jdbc = bulkJdbcTemplate;
        this.objectMapper = objectMapper;
        this.batchSize = Math.max(1, properties.getBatchSize());
    }

    /** Registers (or merges) a logical bulk run before any items are written. */
    public void ensureRun(
            UUID runId, String pipelineId, String language, LexicalSearchParams tuning, int expectedChunkCount) {
        int chunks = Math.max(1, expectedChunkCount);
        String tuningJson = toJson(tuning);
        jdbc.update(
                """
                INSERT INTO bulk_run (id, pipeline_id, language, tuning, status, expected_chunk_count)
                VALUES (?, ?, ?, ?::jsonb, 'RUNNING', ?)
                ON CONFLICT (id) DO UPDATE SET
                  expected_chunk_count = GREATEST(bulk_run.expected_chunk_count, EXCLUDED.expected_chunk_count)
                """,
                runId,
                pipelineId,
                language,
                tuningJson,
                chunks);
    }

    /** Writable handle for one HTTP request; call {@link #close()} in {@code finally}. */
    public Session openSession(UUID runId) {
        return new Session(runId);
    }

    public void markRunFailed(UUID runId, String errorSummary) {
        jdbc.update(
                """
                UPDATE bulk_run SET status = 'ERROR', error_summary = ?, finished_at = now()
                WHERE id = ?
                """,
                truncate(errorSummary, 4000),
                runId);
    }

    private void flushItems(UUID runId, List<BufferedRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        int n = rows.size();
        long t0 = System.nanoTime();
        jdbc.batchUpdate(
                UPSERT_ITEM,
                rows,
                batchSize,
                (ps, row) -> {
                    ps.setObject(1, runId);
                    ps.setInt(2, row.rowIndex());
                    ps.setString(3, row.quotationId());
                    ps.setString(4, row.jsonPayload());
                });
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (log.isDebugEnabled()) {
            log.debug("bulk persistence flush runId={} rows={} batchSize={} jdbcMs={}", runId, n, batchSize, ms);
        } else if (ms > 2000L) {
            log.warn("bulk persistence slow flush runId={} rows={} jdbcMs={}", runId, n, ms);
        }
        rows.clear();
    }

    private String toJson(BulkChapterItemResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize BulkChapterItemResult", e);
        }
    }

    private String toJson(LexicalSearchParams tuning) {
        try {
            return objectMapper.writeValueAsString(tuning);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize LexicalSearchParams", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public final class Session implements AutoCloseable {

        private final UUID runId;
        private final List<BufferedRow> buffer = new ArrayList<>();

        private Session(UUID runId) {
            this.runId = runId;
        }

        public void append(int rowIndex, String quotationId, BulkChapterItemResult result) {
            buffer.add(new BufferedRow(rowIndex, quotationId, toJson(result)));
            if (buffer.size() >= batchSize) {
                flushItems(runId, buffer);
            }
        }

        public void markFailed(String message) {
            flushItems(runId, buffer);
            markRunFailed(runId, message);
        }

        /**
         * Flushes remaining items, records this chunk as DONE, and may mark the whole run DONE when all
         * chunks have completed.
         */
        public void completeChunk(int chunkIndex, int itemsInThisRequest) {
            flushItems(runId, buffer);
            jdbc.update(UPSERT_CHUNK, runId, chunkIndex, itemsInThisRequest);
            maybeFinalizeRun(runId);
        }

        @Override
        public void close() {
            flushItems(runId, buffer);
        }
    }

    private void maybeFinalizeRun(UUID runId) {
        List<Integer> expectedRows =
                jdbc.query(
                        "SELECT expected_chunk_count FROM bulk_run WHERE id = ?",
                        ps -> ps.setObject(1, runId),
                        (rs, rowNum) -> rs.getInt(1));
        if (expectedRows.isEmpty()) {
            return;
        }
        int expected = expectedRows.get(0);
        Long done =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM bulk_run_chunk WHERE run_id = ? AND status = 'DONE'",
                        Long.class,
                        runId);
        if (done == null || done < expected) {
            return;
        }
        int updated =
                jdbc.update(
                        """
                        UPDATE bulk_run SET status = 'DONE', finished_at = now(),
                          item_count = (SELECT COUNT(*)::int FROM bulk_run_item WHERE run_id = ?)
                        WHERE id = ? AND status <> 'ERROR'
                        """,
                        runId,
                        runId);
        if (updated > 0) {
            log.info("bulk persistence run finalized runId={} itemCountUpdated={}", runId, updated);
        }
    }

    private record BufferedRow(int rowIndex, String quotationId, String jsonPayload) {}
}
