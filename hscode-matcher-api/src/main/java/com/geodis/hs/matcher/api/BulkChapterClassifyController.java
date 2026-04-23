package com.geodis.hs.matcher.api;

import com.geodis.hs.matcher.api.dto.bulk.BulkChapterClassifyRequest;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterClassifyResponse;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterItemRequest;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterItemResult;
import com.geodis.hs.matcher.api.dto.bulk.BulkChapterSummary;
import com.geodis.hs.matcher.classify.ChapterAggregation;
import com.geodis.hs.matcher.classify.ChapterCandidate;
import com.geodis.hs.matcher.classify.LuceneChapterClassifier;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.bulk.persistence.BulkChapterPersistenceService;
import com.geodis.hs.matcher.llm.BulkLlmProperties;
import com.geodis.hs.matcher.llm.LlmChapterRefinement;
import com.geodis.hs.matcher.llm.LlmChapterRefinementService;
import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class BulkChapterClassifyController {

    private static final Logger log = LoggerFactory.getLogger(BulkChapterClassifyController.class);

    public static final int MAX_ITEMS = 5000;
    private static final int DEFAULT_SEARCH_LIMIT = 15;
    private static final int MAX_SEARCH_LIMIT = 50;
    private static final String PIPELINE_ID = "lucene_bm25_fuzzy_chapter_llm_v1";

    /** Log a progress line at most every N rows/items (plus first batch and last row). */
    private static final int BULK_PROGRESS_LOG_INTERVAL = 100;

    /** Counters for diagnosing slow bulk runs (wall time vs Lucene vs LLM vs dedup). */
    private static final class BulkRunStats {
        int dedupReuses;
        int uniqueClassifies;
        long sumLuceneMs;
        long sumLlmMs;
        int llmCalls;

        int totalRows() {
            return dedupReuses + uniqueClassifies;
        }

        void onDedupReuse() {
            dedupReuses++;
        }

        void onUniqueClassify(BulkChapterItemResult r) {
            uniqueClassifies++;
            sumLuceneMs += Math.max(0L, r.latencyMsLucene());
            long lm = Math.max(0L, r.latencyMsLlm());
            sumLlmMs += lm;
            if (lm > 0L) {
                llmCalls++;
            }
        }
    }

    private static void logBulkPhaseSummary(String mode, String runId, long wallMs, BulkRunStats s) {
        if (s == null) {
            return;
        }
        int rows = s.totalRows();
        if (rows <= 0) {
            return;
        }
        long accounted = s.sumLuceneMs + s.sumLlmMs;
        long gap = Math.max(0L, wallMs - accounted);
        double avgWallPerRow = wallMs / (double) rows;
        double avgLucene = s.uniqueClassifies > 0 ? s.sumLuceneMs / (double) s.uniqueClassifies : 0.0;
        double avgLlmPerCall = s.llmCalls > 0 ? s.sumLlmMs / (double) s.llmCalls : 0.0;
        log.info(
                "bulk chapter {} phase summary runId={} wallMs={} rows={} dedupReuses={} uniqueClassify={} "
                        + "sumLuceneMs={} sumLlmMs={} llmCalls={} avgWallMsPerRow={} avgLuceneMsPerUnique={} "
                        + "avgLlmMsPerLlmCall={} unaccountedMs={} (parsing, CSV/JSON, persistence, GC, etc.)",
                mode,
                runId,
                wallMs,
                rows,
                s.dedupReuses,
                s.uniqueClassifies,
                s.sumLuceneMs,
                s.sumLlmMs,
                s.llmCalls,
                round3(avgWallPerRow),
                round3(avgLucene),
                round3(avgLlmPerCall),
                gap);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static final List<String> EXTRA_CSV_COLUMNS =
            List.of(
                    "chapter_lucene",
                    "chapter_title_lucene",
                    "confidence_lucene",
                    "top3_chapters_lucene",
                    "top3_scores_lucene",
                    "ambiguous_lucene",
                    "low_information_lucene",
                    "error_lucene",
                    "chapter_llm",
                    "confidence_llm",
                    "top3_chapters_llm",
                    "ambiguous_llm",
                    "error_llm",
                    "rationale_llm",
                    "latency_ms_llm",
                    "agree_chapter",
                    "latency_ms_lucene",
                    "run_id");

    private final NomenclatureSearchRuntime searchRuntime;
    private final LuceneChapterClassifier luceneChapterClassifier;
    private final LlmChapterRefinementService llmChapterRefinementService;
    private final BulkChapterOutputService bulkChapterOutputService;
    private final ObjectProvider<BulkChapterPersistenceService> bulkChapterPersistenceService;
    private final BulkLlmProperties bulkLlmProperties;

    public BulkChapterClassifyController(
            NomenclatureSearchRuntime searchRuntime,
            LuceneChapterClassifier luceneChapterClassifier,
            LlmChapterRefinementService llmChapterRefinementService,
            BulkChapterOutputService bulkChapterOutputService,
            ObjectProvider<BulkChapterPersistenceService> bulkChapterPersistenceService,
            BulkLlmProperties bulkLlmProperties) {
        this.searchRuntime = searchRuntime;
        this.luceneChapterClassifier = luceneChapterClassifier;
        this.llmChapterRefinementService = llmChapterRefinementService;
        this.bulkChapterOutputService = bulkChapterOutputService;
        this.bulkChapterPersistenceService = bulkChapterPersistenceService;
        this.bulkLlmProperties = bulkLlmProperties;
    }

    /** Allocates a new {@code runId} (UUID) for parallel bulk chunks; insert into DB happens on first classify call. */
    @PostMapping("/bulk/chapter-classify/runs")
    public ResponseEntity<Map<String, String>> createBulkRunId() {
        return ResponseEntity.ok(Map.of("runId", UUID.randomUUID().toString()));
    }

    @PostMapping(value = "/bulk/chapter-classify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BulkChapterClassifyResponse> classifyJson(
            @RequestBody BulkChapterClassifyRequest request,
            @RequestHeader(value = "X-Bulk-Run-Id", required = false) String headerRunId,
            @RequestParam(value = "runId", required = false) String queryRunId,
            @RequestParam(value = "chunkIndex", defaultValue = "0") int chunkIndex,
            @RequestParam(value = "chunkTotal", defaultValue = "1") int chunkTotal,
            @RequestParam(value = "rowIndexOffset", defaultValue = "0") int rowIndexOffset)
            throws IOException {
        Language language = parseLang(request.lang());
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        validateItems(request.items());
        LexicalSearchParams tuning = tuningFrom(request);
        if (!tuning.isRunnable()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At least one of fuzzy or bm25 must be true");
        }
        validateChunkParams(chunkIndex, chunkTotal, rowIndexOffset);
        int lim = searchLimit(request.searchLimit());
        UUID runUuid = resolveRunId(headerRunId, queryRunId);
        String runId = runUuid.toString();
        int total = request.items().size();
        long t0 = System.nanoTime();
        log.info(
                "bulk chapter JSON started runId={} lang={} items={} searchLimit={} chunk={}/{} rowIndexOffset={}",
                runId,
                language,
                total,
                lim,
                chunkIndex,
                chunkTotal,
                rowIndexOffset);
        warnIfBulkMayBeSlow(total, "JSON");

        BulkChapterPersistenceService persistence = bulkChapterPersistenceService.getIfAvailable();
        BulkChapterPersistenceService.Session persistSession = null;
        long tEnsure = System.nanoTime();
        if (persistence != null) {
            persistence.ensureRun(runUuid, PIPELINE_ID, language.name(), tuning, chunkTotal);
            persistSession = persistence.openSession(runUuid);
        }
        long ensureMs = elapsedMs(tEnsure);
        if (persistence != null) {
            log.info("bulk chapter JSON persistence init runId={} ensureRun+openSessionMs={}", runId, ensureMs);
        }

        List<BulkChapterItemResult> results = new ArrayList<>(total);
        Map<String, BulkChapterItemResult> dedupByDescription = new HashMap<>();
        BulkRunStats stats = new BulkRunStats();
        try {
            int done = 0;
            for (int i = 0; i < request.items().size(); i++) {
                BulkChapterItemRequest item = request.items().get(i);
                BulkChapterItemResult r =
                        classifyOneWithDescriptionDedup(
                                item, language, tuning, lim, dedupByDescription, stats);
                results.add(r);
                if (persistSession != null) {
                    int rowIndex = rowIndexOffset + i;
                    persistSession.append(rowIndex, item.id() == null ? "" : item.id(), r);
                }
                done++;
                logBulkProgress("JSON", runId, done, total);
            }
            if (persistSession != null) {
                persistSession.completeChunk(chunkIndex, total);
            }
            long wall = elapsedMs(t0);
            log.info(
                    "bulk chapter JSON completed runId={} items={} elapsedMs={}",
                    runId,
                    total,
                    wall);
            logBulkPhaseSummary("JSON", runId, wall, stats);
            BulkChapterSummary summary = summarize(results);
            BulkChapterClassifyResponse withoutPath =
                    new BulkChapterClassifyResponse(
                            runId, language.name(), PIPELINE_ID, tuning, results, summary, null);
            long tw = System.nanoTime();
            Optional<Path> written = bulkChapterOutputService.writeJsonIfConfigured(runId, withoutPath);
            if (written.isPresent()) {
                log.info(
                        "bulk chapter JSON output file runId={} path={} writeMs={}",
                        runId,
                        written.get(),
                        elapsedMs(tw));
            }
            BulkChapterClassifyResponse body =
                    new BulkChapterClassifyResponse(
                            runId,
                            language.name(),
                            PIPELINE_ID,
                            tuning,
                            results,
                            summary,
                            written.map(Path::toString).orElse(null));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            if (persistSession != null) {
                persistSession.markFailed(String.valueOf(e.getMessage()));
            }
            throw e;
        } finally {
            if (persistSession != null) {
                persistSession.close();
            }
        }
    }

    @PostMapping(
            value = "/bulk/chapter-classify",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> classifyCsv(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "lang", defaultValue = "FR") String lang,
            @RequestParam(value = "searchLimit", required = false) Integer searchLimitParam,
            @RequestHeader(value = "X-Bulk-Run-Id", required = false) String headerRunId,
            @RequestParam(value = "runId", required = false) String queryRunId,
            @RequestParam(value = "chunkIndex", defaultValue = "0") int chunkIndex,
            @RequestParam(value = "chunkTotal", defaultValue = "1") int chunkTotal,
            @RequestParam(value = "rowIndexOffset", defaultValue = "0") int rowIndexOffset,
            @RequestParam(required = false) Boolean fuzzy,
            @RequestParam(required = false) Boolean bm25,
            @RequestParam(required = false) Float fuzzyBoost,
            @RequestParam(required = false) Integer minFuzzyTokenLength,
            @RequestParam(required = false) Integer fuzzyMaxExpansions,
            @RequestParam(required = false) Integer fuzzyPrefixLength,
            @RequestParam(required = false) Integer maxEditsShort,
            @RequestParam(required = false) Integer maxEditsLong,
            @RequestParam(required = false) Integer maxQueryChars)
            throws IOException {
        Language language = parseLang(lang);
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        LexicalSearchParams tuning =
                LexicalSearchParams.merge(
                        fuzzy,
                        bm25,
                        fuzzyBoost,
                        minFuzzyTokenLength,
                        fuzzyMaxExpansions,
                        fuzzyPrefixLength,
                        maxEditsShort,
                        maxEditsLong,
                        maxQueryChars);
        if (!tuning.isRunnable()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At least one of fuzzy or bm25 must be true");
        }
        validateChunkParams(chunkIndex, chunkTotal, rowIndexOffset);
        int lim = searchLimit(searchLimitParam);
        UUID runUuid = resolveRunId(headerRunId, queryRunId);
        String runId = runUuid.toString();
        log.info(
                "bulk chapter CSV received runId={} lang={} originalFilename={} sizeBytes={} searchLimit={} chunk={}/{} rowIndexOffset={}",
                runId,
                language,
                file.getOriginalFilename(),
                file.getSize(),
                lim,
                chunkIndex,
                chunkTotal,
                rowIndexOffset);
        try (CSVParser parser = openMyGeodisCsv(file)) {
            BulkChapterPersistenceService persistence = bulkChapterPersistenceService.getIfAvailable();
            BulkChapterPersistenceService.Session persistSession = null;
            long tEnsure = System.nanoTime();
            if (persistence != null) {
                persistence.ensureRun(runUuid, PIPELINE_ID, language.name(), tuning, chunkTotal);
                persistSession = persistence.openSession(runUuid);
            }
            long ensureMs = elapsedMs(tEnsure);
            if (persistence != null) {
                log.info("bulk chapter CSV persistence init runId={} ensureRun+openSessionMs={}", runId, ensureMs);
            }
            try {
                long t0 = System.nanoTime();
                CsvRenderOutcome rendered =
                        renderBulkChapterCsv(
                                parser, language, tuning, lim, runId, rowIndexOffset, persistSession);
                byte[] bytes = rendered.bytes();
                long wall = elapsedMs(t0);
                log.info(
                        "bulk chapter CSV completed runId={} responseBytes={} elapsedMs={}",
                        runId,
                        bytes.length,
                        wall);
                logBulkPhaseSummary("CSV", runId, wall, rendered.stats());
                Optional<Path> written = bulkChapterOutputService.writeCsvIfConfigured(runId, bytes);
                written.ifPresent(
                        p -> log.info("bulk chapter CSV result file runId={} path={}", runId, p));
                if (persistSession != null) {
                    persistSession.completeChunk(chunkIndex, rendered.dataRowCount());
                }
                return csvAttachment(runId, bytes, written);
            } catch (Exception e) {
                if (persistSession != null) {
                    persistSession.markFailed(String.valueOf(e.getMessage()));
                }
                throw e;
            } finally {
                if (persistSession != null) {
                    persistSession.close();
                }
            }
        }
    }

    private record CsvRenderOutcome(byte[] bytes, int dataRowCount, BulkRunStats stats) {}

    private CsvRenderOutcome renderBulkChapterCsv(
            CSVParser parser,
            Language language,
            LexicalSearchParams tuning,
            int lim,
            String runId,
            int rowIndexOffset,
            BulkChapterPersistenceService.Session persistSession)
            throws IOException {
        long tWhole = System.nanoTime();
        long tParse = System.nanoTime();
        List<CSVRecord> records = parser.getRecords();
        long parseMs = elapsedMs(tParse);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV has no data rows");
        }
        if (records.size() > MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many CSV rows (max " + MAX_ITEMS + ")");
        }
        log.info(
                "bulk chapter CSV runId={} parsed {} data rows in {} ms; classifying...",
                runId,
                records.size(),
                parseMs);
        BulkRunStats stats = new BulkRunStats();
        warnIfBulkMayBeSlow(records.size(), "CSV");
        List<String> headerNames = new ArrayList<>(parser.getHeaderNames());
        int colId = headerIndex(headerNames, "quotation id", "quotation_id", "id");
        int colDesc =
                headerIndex(headerNames, "product description", "product_description", "description");
        if (colDesc < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CSV must contain a product description column (e.g. Product Description); found headers: "
                            + headerNames);
        }
        String keyId = colId >= 0 ? headerNames.get(colId) : null;
        String keyDesc = headerNames.get(colDesc);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(bout, StandardCharsets.UTF_8);
                CSVPrinter printer =
                        new CSVPrinter(
                                writer,
                                CSVFormat.Builder.create(CSVFormat.RFC4180)
                                        .setDelimiter(';')
                                        .build())) {
            List<String> outHeader = new ArrayList<>(headerNames);
            outHeader.addAll(EXTRA_CSV_COLUMNS);
            printer.printRecord(outHeader);
            int row = 0;
            long tClassify = System.nanoTime();
            Map<String, BulkChapterItemResult> dedupByDescription = new HashMap<>();
            for (CSVRecord rec : records) {
                String id =
                        keyId != null && rec.isMapped(keyId) ? rec.get(keyId) : ("row-" + (row + 1));
                String description = rec.isMapped(keyDesc) ? rec.get(keyDesc) : "";
                BulkChapterItemResult r =
                        classifyOneWithDescriptionDedup(
                                new BulkChapterItemRequest(id, description),
                                language,
                                tuning,
                                lim,
                                dedupByDescription,
                                stats);
                if (persistSession != null) {
                    persistSession.append(rowIndexOffset + row, id, r);
                }
                List<String> line = new ArrayList<>();
                for (String h : headerNames) {
                    line.add(rec.isMapped(h) ? rec.get(h) : "");
                }
                line.addAll(toCsvExtraColumns(r, runId));
                printer.printRecord(line);
                row++;
                logBulkProgress("CSV", runId, row, records.size());
            }
            log.info(
                    "bulk chapter CSV runId={} classification loop finished rows={} loopElapsedMs={}",
                    runId,
                    records.size(),
                    elapsedMs(tClassify));
        }
        long tSerialize = System.nanoTime();
        byte[] outBytes = bout.toByteArray();
        long serializeMs = elapsedMs(tSerialize);
        log.info(
                "bulk chapter CSV runId={} serializeResponseMs={} renderBulkChapterCsvTotalMs={}",
                runId,
                serializeMs,
                elapsedMs(tWhole));
        return new CsvRenderOutcome(outBytes, records.size(), stats);
    }

    private static CSVParser openMyGeodisCsv(MultipartFile file) throws IOException {
        return CSVFormat.Builder.create(CSVFormat.RFC4180)
                .setDelimiter(';')
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(false)
                .build()
                .parse(
                        new InputStreamReader(
                                skipUtf8Bom(file.getInputStream()), StandardCharsets.UTF_8));
    }

    private static ResponseEntity<byte[]> csvAttachment(String runId, byte[] bytes, Optional<Path> writtenFile) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("chapter-classify-" + runId + ".csv", StandardCharsets.UTF_8)
                        .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        writtenFile.ifPresent(p -> headers.add("X-Bulk-Output-File", p.toString()));
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    private static List<String> toCsvExtraColumns(BulkChapterItemResult r, String runId) {
        List<String> topCodes = new ArrayList<>();
        List<String> topScores = new ArrayList<>();
        for (ChapterCandidate c : r.top3Lucene()) {
            topCodes.add(c.code());
            topScores.add(Double.toString(c.score()));
        }
        return List.of(
                nullToEmpty(r.chapterLucene()),
                nullToEmpty(r.chapterTitleLucene()),
                Double.toString(r.confidenceLucene()),
                String.join("|", topCodes),
                String.join("|", topScores),
                Boolean.toString(r.ambiguousLucene()),
                Boolean.toString(r.lowInformationLucene()),
                nullToEmpty(r.errorCodeLucene()),
                nullToEmpty(r.chapterLlm()),
                r.confidenceLlm() == null ? "" : Double.toString(r.confidenceLlm()),
                nullToEmpty(r.top3ChaptersLlm()),
                r.ambiguousLlm() == null ? "" : Boolean.toString(r.ambiguousLlm()),
                nullToEmpty(r.errorLlm()),
                nullToEmpty(r.rationaleLlm()),
                Long.toString(r.latencyMsLlm()),
                r.agreeChapter() == null ? "" : Boolean.toString(r.agreeChapter()),
                Long.toString(r.latencyMsLucene()),
                runId);
    }

    private BulkChapterItemResult classifyOne(
            BulkChapterItemRequest item, Language language, LexicalSearchParams tuning, int searchLimit)
            throws IOException {
        long t0 = System.nanoTime();
        ChapterAggregation a =
                luceneChapterClassifier.classify(language, item.description(), tuning, searchLimit);
        long msLucene = (System.nanoTime() - t0) / 1_000_000L;
        String id = item.id() == null ? "" : item.id();
        String desc = item.description() == null ? "" : item.description();

        String chapterLlm = null;
        Double confidenceLlm = null;
        String top3ChaptersLlm = null;
        Boolean ambiguousLlm = null;
        String errorLlm = null;
        String rationaleLlm = null;
        long msLlm = 0L;
        Boolean agreeChapter = null;

        long tLlm = System.nanoTime();
        Optional<LlmChapterRefinement> llm = llmChapterRefinementService.refine(desc, language, a);
        if (llm.isPresent()) {
            msLlm = (System.nanoTime() - tLlm) / 1_000_000L;
            LlmChapterRefinement r = llm.get();
            if (r.error() != null) {
                errorLlm = r.error();
                ambiguousLlm = true;
            } else {
                chapterLlm = r.chapter();
                confidenceLlm = r.confidence01();
                ambiguousLlm = r.ambiguous();
                rationaleLlm = r.rationale();
                agreeChapter =
                        a.bestChapterCode() != null
                                && chapterLlm != null
                                && a.bestChapterCode().equals(chapterLlm);
            }
        }

        return new BulkChapterItemResult(
                id,
                desc,
                a.bestChapterCode(),
                a.bestChapterDescription(),
                a.confidence01(),
                a.top3(),
                a.ambiguous(),
                a.lowInformation(),
                a.errorCode(),
                chapterLlm,
                confidenceLlm,
                top3ChaptersLlm,
                ambiguousLlm,
                errorLlm,
                rationaleLlm,
                msLlm,
                agreeChapter,
                msLucene);
    }

    private BulkChapterItemResult classifyOneWithDescriptionDedup(
            BulkChapterItemRequest item,
            Language language,
            LexicalSearchParams tuning,
            int searchLimit,
            Map<String, BulkChapterItemResult> dedupByDescription,
            BulkRunStats stats)
            throws IOException {
        String key = BulkChapterDescriptionDedup.normalizedDescriptionKey(item.description());
        BulkChapterItemResult cached = dedupByDescription.get(key);
        if (cached != null) {
            if (stats != null) {
                stats.onDedupReuse();
            }
            return cached.forRow(item.id(), item.description());
        }
        BulkChapterItemResult r = classifyOne(item, language, tuning, searchLimit);
        dedupByDescription.put(key, r);
        if (stats != null) {
            stats.onUniqueClassify(r);
        }
        return r;
    }

    private void warnIfBulkMayBeSlow(int rowCount, String formatLabel) {
        if (rowCount < 50) {
            return;
        }
        if (!bulkLlmProperties.isEnabled()) {
            return;
        }
        if (bulkLlmProperties.isOnlyWhenAmbiguous()) {
            return;
        }
        log.warn(
                "bulk chapter {}: {} rows with bulk.llm.enabled=true and bulk.llm.only-when-ambiguous=false "
                        + "(~one LLM call per row — very slow). Prefer only-when-ambiguous=true or disable the LLM for large files. "
                        + "Identical descriptions in this request share one classify path.",
                formatLabel,
                rowCount);
    }

    private static BulkChapterSummary summarize(List<BulkChapterItemResult> items) {
        int n = items.size();
        int present = 0;
        int amb = 0;
        int errOrEmpty = 0;
        double confSum = 0;
        for (BulkChapterItemResult r : items) {
            if (r.chapterLucene() != null && !r.chapterLucene().isBlank()) {
                present++;
                confSum += r.confidenceLucene();
            }
            if (r.ambiguousLucene()) {
                amb++;
            }
            if (r.errorCodeLucene() != null) {
                errOrEmpty++;
            }
        }
        double avg = present > 0 ? confSum / present : 0.0;
        return new BulkChapterSummary(n, present, amb, errOrEmpty, avg);
    }

    private static LexicalSearchParams tuningFrom(BulkChapterClassifyRequest request) {
        return LexicalSearchParams.merge(
                request.fuzzy(),
                request.bm25(),
                request.fuzzyBoost(),
                request.minFuzzyTokenLength(),
                request.fuzzyMaxExpansions(),
                request.fuzzyPrefixLength(),
                request.maxEditsShort(),
                request.maxEditsLong(),
                request.maxQueryChars());
    }

    private static int searchLimit(Integer requested) {
        int lim = requested == null ? DEFAULT_SEARCH_LIMIT : requested;
        return Math.min(Math.max(1, lim), MAX_SEARCH_LIMIT);
    }

    private static void validateItems(List<BulkChapterItemRequest> items) {
        if (items == null || items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "items must be non-empty");
        }
        if (items.size() > MAX_ITEMS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many items (max " + MAX_ITEMS + ")");
        }
    }

    private static Language parseLang(String lang) {
        if (lang == null || lang.isBlank()) {
            return Language.FR;
        }
        try {
            return Language.valueOf(lang.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid lang: " + lang);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normHeader(String h) {
        if (h == null) {
            return "";
        }
        String s = h.strip();
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1).strip();
        }
        return s;
    }

    /** Index of first header matching one of {@code aliases} (case-insensitive, normalized). */
    private static int headerIndex(List<String> headers, String... aliases) {
        for (int i = 0; i < headers.size(); i++) {
            String hi = normHeader(headers.get(i)).toLowerCase(Locale.ROOT);
            for (String a : aliases) {
                if (hi.equalsIgnoreCase(a)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static void logBulkProgress(String mode, String runId, int done, int total) {
        if (total <= 0) {
            return;
        }
        if (done == 1
                || done == total
                || done % BULK_PROGRESS_LOG_INTERVAL == 0) {
            int pct = (int) ((100L * done) / total);
            log.info("bulk chapter {} progress runId={} {}/{} ({}%)", mode, runId, done, total, pct);
        }
    }

    private static long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private static UUID resolveRunId(String headerRunId, String queryRunId) {
        String raw = null;
        if (headerRunId != null && !headerRunId.isBlank()) {
            raw = headerRunId.strip();
        } else if (queryRunId != null && !queryRunId.isBlank()) {
            raw = queryRunId.strip();
        }
        if (raw == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid runId: expected UUID");
        }
    }

    private static void validateChunkParams(int chunkIndex, int chunkTotal, int rowIndexOffset) {
        if (chunkTotal < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkTotal must be >= 1");
        }
        if (chunkIndex < 0 || chunkIndex >= chunkTotal) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chunkIndex must be in [0, chunkTotal)");
        }
        if (rowIndexOffset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rowIndexOffset must be >= 0");
        }
    }

    private static InputStream skipUtf8Bom(InputStream in) throws IOException {
        PushbackInputStream pin = new PushbackInputStream(new BufferedInputStream(in), 3);
        int b1 = pin.read();
        if (b1 < 0) {
            return pin;
        }
        int b2 = pin.read();
        if (b2 < 0) {
            pin.unread(b1);
            return pin;
        }
        int b3 = pin.read();
        if (b3 < 0) {
            pin.unread(new byte[] {(byte) b1, (byte) b2});
            return pin;
        }
        if (b1 == 0xEF && b2 == 0xBB && b3 == 0xBF) {
            return pin;
        }
        pin.unread(new byte[] {(byte) b1, (byte) b2, (byte) b3});
        return pin;
    }
}
