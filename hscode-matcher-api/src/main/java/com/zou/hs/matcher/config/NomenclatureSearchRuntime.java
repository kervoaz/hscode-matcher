package com.zou.hs.matcher.config;

import com.zou.hs.matcher.api.dto.ExplainFusionRow;
import com.zou.hs.matcher.api.dto.ExplainLexicalRow;
import com.zou.hs.matcher.api.dto.ExplainSemanticRow;
import com.zou.hs.matcher.api.dto.SearchExplainPayload;
import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import com.zou.hs.matcher.search.LexicalHit;
import com.zou.hs.matcher.search.LexicalSearchOutcome;
import com.zou.hs.matcher.search.OutcomeMeta;
import com.zou.hs.matcher.search.PerRequestSearchOptions;
import com.zou.hs.matcher.search.SearchHit;
import com.zou.hs.matcher.search.UnifiedSearchOutcome;
import com.zou.hs.matcher.search.embedding.EmbeddingEngine;
import com.zou.hs.matcher.search.embedding.LanguageEmbeddingIndex;
import com.zou.hs.matcher.search.hybrid.HybridMerger;
import com.zou.hs.matcher.search.hybrid.HybridMerger.FusionExplainRow;
import com.zou.hs.matcher.search.lucene.LexicalQueryOptions;
import com.zou.hs.matcher.search.model.ScoredHit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Holds the current {@link NomenclatureIndexBundle} and supports atomic reload. Reads use the bundle
 * snapshot without locking so searches are not blocked during rebuild.
 */
@Service
public class NomenclatureSearchRuntime implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureSearchRuntime.class);

    private static final int MAX_CANDIDATE_POOL = 200;
    private static final int EXPLAIN_FUSION_CAP = 50;
    private static final int EXPLAIN_RANK_CAP = 100;

    private final NomenclatureCsvProperties csvProperties;
    private final AtomicReference<NomenclatureIndexBundle> bundleRef;
    private final EmbeddingEngine embeddingEngine;
    private final MeterRegistry meterRegistry;
    private final Optional<Path> embeddingCacheDirectory;
    private final String embeddingCacheSalt;
    private final int rrfK;
    private final int lexicalPoolMultiplier;
    private final int defaultEmbedTimeoutMs;
    private final int defaultMinHybridQueryChars;
    private final ExecutorService embedExecutor;

    public NomenclatureSearchRuntime(
            NomenclatureCsvProperties csvProperties,
            ObjectProvider<EmbeddingEngine> embeddingEngineProvider,
            MeterRegistry meterRegistry,
            HsMatcherEmbeddingProperties embeddingCacheProperties,
            @Value("${hs.matcher.hybrid.rrf-k:60}") int rrfK,
            @Value("${hs.matcher.hybrid.lexical-pool-multiplier:8}") int lexicalPoolMultiplier,
            @Value("${hs.matcher.embed.timeout-ms:0}") int defaultEmbedTimeoutMs,
            @Value("${hs.matcher.hybrid.min-query-chars:0}") int defaultMinHybridQueryChars)
            throws IOException {
        this.csvProperties = csvProperties;
        this.embeddingEngine = embeddingEngineProvider.getIfAvailable();
        this.meterRegistry = meterRegistry;
        this.rrfK = rrfK;
        this.lexicalPoolMultiplier = Math.max(1, lexicalPoolMultiplier);
        this.defaultEmbedTimeoutMs = Math.max(0, defaultEmbedTimeoutMs);
        this.defaultMinHybridQueryChars = Math.max(0, defaultMinHybridQueryChars);
        String dir = embeddingCacheProperties.getCacheDir();
        if (dir == null || dir.isBlank()) {
            this.embeddingCacheDirectory = Optional.empty();
        } else {
            this.embeddingCacheDirectory = Optional.of(Path.of(dir.trim()).toAbsolutePath().normalize());
        }
        String salt = embeddingCacheProperties.getCacheSalt();
        this.embeddingCacheSalt = salt == null || salt.isBlank() ? "1" : salt;
        this.embedExecutor =
                Executors.newCachedThreadPool(
                        r -> {
                            Thread t = new Thread(r, "hs-matcher-embed");
                            t.setDaemon(true);
                            return t;
                        });
        this.bundleRef =
                new AtomicReference<>(
                        NomenclatureIndexBundle.load(
                                csvProperties,
                                Optional.ofNullable(this.embeddingEngine),
                                embeddingCacheDirectory,
                                embeddingCacheSalt));
    }

    public boolean isReady(Language language) {
        NomenclatureIndexBundle b = bundleRef.get();
        return b != null && b.lexicalSearchService().isReady(language);
    }

    public Optional<NomenclatureRegistry> registry(Language language) {
        NomenclatureIndexBundle b = bundleRef.get();
        return b == null ? Optional.empty() : b.lexicalSearchService().registry(language);
    }

    public UnifiedSearchOutcome search(Language language, String queryText, int limit) throws IOException {
        return search(language, queryText, limit, PerRequestSearchOptions.defaults());
    }

    public UnifiedSearchOutcome search(
            Language language, String queryText, int limit, PerRequestSearchOptions options)
            throws IOException {
        long t0 = System.nanoTime();
        try {
            UnifiedSearchOutcome out = searchWithoutMetrics(language, queryText, limit, options);
            recordSearch(language, out, System.nanoTime() - t0, false);
            return out;
        } catch (IOException e) {
            recordSearch(language, UnifiedSearchOutcome.empty(), System.nanoTime() - t0, true);
            throw e;
        }
    }

    public int configuredRrfK() {
        return rrfK;
    }

    public int configuredLexicalPoolMultiplier() {
        return lexicalPoolMultiplier;
    }

    public int defaultEmbedTimeoutMs() {
        return defaultEmbedTimeoutMs;
    }

    public int defaultMinHybridQueryChars() {
        return defaultMinHybridQueryChars;
    }

    private void recordSearch(Language language, UnifiedSearchOutcome out, long nanos, boolean error) {
        String hybridTag = error ? "n/a" : Boolean.toString(out.hybridEnabled());
        Timer.builder("hs.matcher.search")
                .description("Nomenclature free-text search latency")
                .tag("language", language.name())
                .tag("hybrid", hybridTag)
                .tag("result", error ? "error" : "success")
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    private UnifiedSearchOutcome searchWithoutMetrics(
            Language language, String queryText, int limit, PerRequestSearchOptions options)
            throws IOException {
        NomenclatureIndexBundle b = bundleRef.get();
        if (b == null) {
            return UnifiedSearchOutcome.empty();
        }
        int mult =
                options.poolMultiplierOverride() > 0
                        ? options.poolMultiplierOverride()
                        : lexicalPoolMultiplier;
        int pool = candidatePool(limit, mult);
        LexicalQueryOptions lexOpts =
                new LexicalQueryOptions(options.fuzzyEnabled(), options.minFuzzyTokenLengthOverride());
        int effectiveMinFuzz = lexOpts.effectiveMinFuzzyTokenLength();

        if (queryText == null || queryText.isBlank()) {
            return new UnifiedSearchOutcome(List.of(), 0, false, pool, OutcomeMeta.empty());
        }
        String trim = queryText.trim();
        int serverMinHybrid = options.minHybridQueryChars();
        boolean hybridSkippedShort =
                options.allowHybrid() && serverMinHybrid > 0 && trim.length() < serverMinHybrid;
        OutcomeMeta baseMeta =
                new OutcomeMeta(
                        null,
                        hybridSkippedShort,
                        false,
                        false,
                        !options.fuzzyEnabled(),
                        effectiveMinFuzz,
                        serverMinHybrid);

        LexicalSearchOutcome lex =
                b.lexicalSearchService().search(language, queryText, pool, lexOpts);
        Optional<LanguageEmbeddingIndex> semOpt = b.semanticIndex(language);
        int k = options.rrfKOverride() > 0 ? options.rrfKOverride() : rrfK;

        boolean hybridPath = options.allowHybrid() && !hybridSkippedShort;
        if (!hybridPath || semOpt.isEmpty() || embeddingEngine == null) {
            SearchExplainPayload ex = explainLexicalOnly(options, lex);
            return UnifiedSearchOutcome.fromLexicalOnly(
                    lex, limit, pool, withExplain(baseMeta, ex));
        }
        return fuseWithSemantic(b, language, queryText, limit, lex, semOpt.get(), pool, k, options, baseMeta);
    }

    private static OutcomeMeta withExplain(OutcomeMeta base, SearchExplainPayload explain) {
        return new OutcomeMeta(
                explain,
                base.hybridSkippedShortQuery(),
                base.embeddingFallback(),
                base.embeddingTimedOut(),
                base.fuzzyDisabledByRequest(),
                base.effectiveMinFuzzyTokenLength(),
                base.serverMinHybridQueryChars());
    }

    private SearchExplainPayload explainLexicalOnly(PerRequestSearchOptions options, LexicalSearchOutcome lex) {
        if (!options.explain()) {
            return null;
        }
        int n = Math.min(lex.hits().size(), EXPLAIN_RANK_CAP);
        List<ExplainLexicalRow> lr = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LexicalHit h = lex.hits().get(i);
            lr.add(new ExplainLexicalRow(h.entry().code(), i + 1, h.score()));
        }
        return new SearchExplainPayload(lr, List.of(), List.of());
    }

    private UnifiedSearchOutcome fuseWithSemantic(
            NomenclatureIndexBundle b,
            Language language,
            String queryText,
            int limit,
            LexicalSearchOutcome lex,
            LanguageEmbeddingIndex sem,
            int pool,
            int rrfKForRequest,
            PerRequestSearchOptions options,
            OutcomeMeta baseMeta)
            throws IOException {
        int timeoutMs =
                options.embedTimeoutMsOverride() > 0 ? options.embedTimeoutMsOverride() : defaultEmbedTimeoutMs;
        EmbedAttempt embed = embedQuery(queryText.trim(), timeoutMs);
        if (embed.vector == null) {
            if (embed.timedOut) {
                log.warn("Query embedding timed out after {} ms", timeoutMs);
            }
            SearchExplainPayload ex = explainLexicalOnly(options, lex);
            OutcomeMeta m =
                    new OutcomeMeta(
                            ex,
                            baseMeta.hybridSkippedShortQuery(),
                            true,
                            embed.timedOut,
                            baseMeta.fuzzyDisabledByRequest(),
                            baseMeta.effectiveMinFuzzyTokenLength(),
                            baseMeta.serverMinHybridQueryChars());
            return UnifiedSearchOutcome.fromLexicalOnly(lex, limit, pool, m);
        }

        List<ScoredHit> semHits = sem.search(embed.vector, pool);
        List<String> lexCodes = lex.hits().stream().map(h -> h.entry().code()).toList();
        List<String> semCodes = semHits.stream().map(ScoredHit::hsCode).toList();
        List<HybridMerger.CodeRrf> merged = HybridMerger.rrfMerge(lexCodes, semCodes, rrfKForRequest);
        if (merged.isEmpty()) {
            SearchExplainPayload ex = buildFullExplain(options, lex, semHits, merged, lexCodes, semCodes);
            OutcomeMeta m =
                    new OutcomeMeta(
                            ex,
                            baseMeta.hybridSkippedShortQuery(),
                            true,
                            false,
                            baseMeta.fuzzyDisabledByRequest(),
                            baseMeta.effectiveMinFuzzyTokenLength(),
                            baseMeta.serverMinHybridQueryChars());
            return UnifiedSearchOutcome.fromLexicalOnly(lex, limit, pool, m);
        }
        Optional<NomenclatureRegistry> regOpt = b.lexicalSearchService().registry(language);
        if (regOpt.isEmpty()) {
            SearchExplainPayload exBadReg = buildFullExplain(options, lex, semHits, merged, lexCodes, semCodes);
            OutcomeMeta mReg =
                    new OutcomeMeta(
                            exBadReg,
                            baseMeta.hybridSkippedShortQuery(),
                            true,
                            false,
                            baseMeta.fuzzyDisabledByRequest(),
                            baseMeta.effectiveMinFuzzyTokenLength(),
                            baseMeta.serverMinHybridQueryChars());
            return UnifiedSearchOutcome.fromLexicalOnly(lex, limit, pool, mReg);
        }
        float maxRrf = merged.get(0).rawRrf();
        List<SearchHit> fused = toHybridHits(merged, regOpt.get(), limit, maxRrf);
        if (fused.isEmpty()) {
            SearchExplainPayload exNoFused = buildFullExplain(options, lex, semHits, merged, lexCodes, semCodes);
            OutcomeMeta mFused =
                    new OutcomeMeta(
                            exNoFused,
                            baseMeta.hybridSkippedShortQuery(),
                            true,
                            false,
                            baseMeta.fuzzyDisabledByRequest(),
                            baseMeta.effectiveMinFuzzyTokenLength(),
                            baseMeta.serverMinHybridQueryChars());
            return UnifiedSearchOutcome.fromLexicalOnly(lex, limit, pool, mFused);
        }
        SearchExplainPayload ex = buildFullExplain(options, lex, semHits, merged, lexCodes, semCodes);
        OutcomeMeta okMeta = withExplain(baseMeta, ex);
        return new UnifiedSearchOutcome(fused, lex.fuzzyTerms(), true, pool, okMeta);
    }

    private SearchExplainPayload buildFullExplain(
            PerRequestSearchOptions options,
            LexicalSearchOutcome lex,
            List<ScoredHit> semHits,
            List<HybridMerger.CodeRrf> merged,
            List<String> lexCodes,
            List<String> semCodes) {
        if (!options.explain()) {
            return null;
        }
        int nl = Math.min(lex.hits().size(), EXPLAIN_RANK_CAP);
        List<ExplainLexicalRow> lr = new ArrayList<>(nl);
        for (int i = 0; i < nl; i++) {
            LexicalHit h = lex.hits().get(i);
            lr.add(new ExplainLexicalRow(h.entry().code(), i + 1, h.score()));
        }
        int ns = Math.min(semHits.size(), EXPLAIN_RANK_CAP);
        List<ExplainSemanticRow> sr = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            ScoredHit sh = semHits.get(i);
            sr.add(new ExplainSemanticRow(sh.hsCode(), i + 1, sh.score()));
        }
        List<FusionExplainRow> fusionRows =
                HybridMerger.fusionExplainRows(lexCodes, semCodes, merged, EXPLAIN_FUSION_CAP);
        List<ExplainFusionRow> fr = new ArrayList<>(fusionRows.size());
        for (FusionExplainRow r : fusionRows) {
            fr.add(new ExplainFusionRow(r.code(), r.lexicalRank(), r.semanticRank(), r.rrfScore()));
        }
        return new SearchExplainPayload(lr, sr, fr);
    }

    private EmbedAttempt embedQuery(String text, int timeoutMs) {
        if (embeddingEngine == null) {
            return new EmbedAttempt(null, false, true);
        }
        if (timeoutMs <= 0) {
            try {
                return new EmbedAttempt(embeddingEngine.embed(text), false, false);
            } catch (Exception ex) {
                log.warn("Query embedding failed; falling back to lexical: {}", ex.toString());
                meterRegistry.counter("hs.matcher.embed", "outcome", "error").increment();
                return new EmbedAttempt(null, false, true);
            }
        }
        Future<float[]> fut =
                embedExecutor.submit(
                        () -> {
                            try {
                                return embeddingEngine.embed(text);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
        try {
            return new EmbedAttempt(fut.get(timeoutMs, TimeUnit.MILLISECONDS), false, false);
        } catch (TimeoutException e) {
            fut.cancel(true);
            meterRegistry.counter("hs.matcher.embed", "outcome", "timeout").increment();
            return new EmbedAttempt(null, true, true);
        } catch (Exception e) {
            log.warn("Query embedding failed; falling back to lexical: {}", e.toString());
            meterRegistry.counter("hs.matcher.embed", "outcome", "error").increment();
            return new EmbedAttempt(null, false, true);
        }
    }

    private record EmbedAttempt(float[] vector, boolean timedOut, boolean failed) {}

    private static List<SearchHit> toHybridHits(
            List<HybridMerger.CodeRrf> merged, NomenclatureRegistry reg, int limit, float maxRrf) {
        return merged.stream()
                .map(row -> reg.get(row.code()).map(e -> newHit(row, e, maxRrf)))
                .flatMap(Optional::stream)
                .limit(limit)
                .toList();
    }

    private static SearchHit newHit(HybridMerger.CodeRrf row, HsEntry e, float maxRrf) {
        float display = maxRrf > 0f ? row.rawRrf() / maxRrf : 0f;
        return new SearchHit(e, display, "HYBRID");
    }

    private static int candidatePool(int limit, int multiplier) {
        int lim = Math.max(1, limit);
        int mult = Math.max(1, multiplier);
        int p = Math.max(lim, lim * mult);
        return Math.min(MAX_CANDIDATE_POOL, p);
    }

    public int candidatePoolForLimit(int limit) {
        return candidatePool(Math.max(1, limit), lexicalPoolMultiplier);
    }

    public int candidatePoolForLimit(int limit, int poolMultiplierOverrideOrZero) {
        int mult = poolMultiplierOverrideOrZero > 0 ? poolMultiplierOverrideOrZero : lexicalPoolMultiplier;
        return candidatePool(Math.max(1, limit), mult);
    }

    public boolean anyLanguageReady() {
        NomenclatureIndexBundle b = bundleRef.get();
        return b != null && b.anyLanguageReady();
    }

    public List<String> lexicalReadyLanguageTags() {
        List<String> tags = new ArrayList<>(3);
        for (Language l : Language.values()) {
            if (isReady(l)) {
                tags.add(l.name());
            }
        }
        return List.copyOf(tags);
    }

    public List<String> hybridReadyLanguageTags() {
        NomenclatureIndexBundle b = bundleRef.get();
        if (b == null || embeddingEngine == null) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(3);
        for (Language l : Language.values()) {
            if (isReady(l) && b.semanticIndex(l).isPresent()) {
                tags.add(l.name());
            }
        }
        return List.copyOf(tags);
    }

    public synchronized void reload() throws IOException {
        long t0 = System.nanoTime();
        try {
            NomenclatureIndexBundle next =
                    NomenclatureIndexBundle.load(
                            csvProperties,
                            Optional.ofNullable(embeddingEngine),
                            embeddingCacheDirectory,
                            embeddingCacheSalt);
            NomenclatureIndexBundle prev = bundleRef.getAndSet(next);
            if (prev != null) {
                prev.close();
            }
            log.info("Nomenclature reload complete (anyLanguageReady={})", next.anyLanguageReady());
            recordReload(System.nanoTime() - t0, "success");
        } catch (IOException e) {
            recordReload(System.nanoTime() - t0, "error");
            throw e;
        }
    }

    private void recordReload(long nanos, String outcome) {
        Timer.builder("hs.matcher.reload")
                .description("Full nomenclature bundle reload (Lucene + optional embeddings)")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void destroy() {
        NomenclatureIndexBundle b = bundleRef.getAndSet(null);
        if (b != null) {
            b.close();
        }
        embedExecutor.shutdown();
    }
}
