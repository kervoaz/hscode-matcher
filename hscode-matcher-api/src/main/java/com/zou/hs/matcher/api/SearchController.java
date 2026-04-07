package com.zou.hs.matcher.api;

import com.zou.hs.matcher.api.dto.MatchRow;
import com.zou.hs.matcher.api.dto.SearchDebugPayload;
import com.zou.hs.matcher.api.dto.SearchResponse;
import com.zou.hs.matcher.config.NomenclatureSearchRuntime;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.search.HierarchyResolver;
import com.zou.hs.matcher.search.OutcomeMeta;
import com.zou.hs.matcher.search.PerRequestSearchOptions;
import com.zou.hs.matcher.search.SearchHit;
import com.zou.hs.matcher.search.lucene.LexicalQueryOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private static final int MAX_LIMIT = 50;
    private static final int RRF_K_MIN = 5;
    private static final int RRF_K_MAX = 200;
    private static final int POOL_MULT_MIN = 1;
    private static final int POOL_MULT_MAX = 24;
    private static final int MIN_FUZZY_TOKEN_LEN_MAX = 32;
    private static final int MIN_HYBRID_CHARS_MAX = 256;
    private static final int EMBED_TIMEOUT_MS_MAX = 120_000;

    private final NomenclatureSearchRuntime searchRuntime;

    public SearchController(NomenclatureSearchRuntime searchRuntime) {
        this.searchRuntime = searchRuntime;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "lang", defaultValue = "FR") String lang,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "hybrid", required = false) Boolean hybrid,
            @RequestParam(value = "rrfK", required = false) Integer rrfK,
            @RequestParam(value = "poolMultiplier", required = false) Integer poolMultiplier,
            @RequestParam(value = "fuzzy", required = false) Boolean fuzzy,
            @RequestParam(value = "minFuzzyTokenLength", required = false) Integer minFuzzyTokenLength,
            @RequestParam(value = "explain", required = false) Boolean explain,
            @RequestParam(value = "minHybridChars", required = false) Integer minHybridChars,
            @RequestParam(value = "embedTimeoutMs", required = false) Integer embedTimeoutMs)
            throws IOException {
        int limCap = Math.min(Math.max(1, limit), MAX_LIMIT);
        boolean hybridSuppressedByRequest = Boolean.FALSE.equals(hybrid);
        boolean allowHybrid = hybrid == null || hybrid;
        boolean fuzzyEnabled = fuzzy == null || fuzzy;
        int rrfOverride = rrfK == null ? 0 : clamp(rrfK, RRF_K_MIN, RRF_K_MAX);
        int poolOverride = poolMultiplier == null ? 0 : clamp(poolMultiplier, POOL_MULT_MIN, POOL_MULT_MAX);
        int effectiveRrfK = rrfK == null ? searchRuntime.configuredRrfK() : rrfOverride;
        int poolHint = searchRuntime.candidatePoolForLimit(limCap, poolOverride);

        int minFuzzOverride =
                minFuzzyTokenLength == null
                        ? 0
                        : clamp(minFuzzyTokenLength, 0, MIN_FUZZY_TOKEN_LEN_MAX);
        LexicalQueryOptions lexOpts = new LexicalQueryOptions(fuzzyEnabled, minFuzzOverride);
        int effectiveMinFuzzyTokenLength = lexOpts.effectiveMinFuzzyTokenLength();

        int resolvedMinHybridChars =
                minHybridChars == null
                        ? searchRuntime.defaultMinHybridQueryChars()
                        : clamp(minHybridChars, 0, MIN_HYBRID_CHARS_MAX);
        int embedTimeoutOverride =
                embedTimeoutMs == null ? 0 : clamp(embedTimeoutMs, 0, EMBED_TIMEOUT_MS_MAX);

        boolean explainRequested = Boolean.TRUE.equals(explain);
        SearchDebugPayload debugBase =
                new SearchDebugPayload(
                        false,
                        false,
                        false,
                        Boolean.FALSE.equals(fuzzy),
                        effectiveMinFuzzyTokenLength,
                        resolvedMinHybridChars);

        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(
                            new SearchResponse(
                                    "",
                                    lang,
                                    0,
                                    List.of(),
                                    false,
                                    0,
                                    false,
                                    poolHint,
                                    hybridSuppressedByRequest,
                                    effectiveRrfK,
                                    null,
                                    debugBase));
        }
        Language language;
        try {
            language = Language.valueOf(lang.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new SearchResponse(
                                    q.trim(),
                                    lang,
                                    0,
                                    List.of(),
                                    false,
                                    0,
                                    false,
                                    poolHint,
                                    hybridSuppressedByRequest,
                                    effectiveRrfK,
                                    null,
                                    debugBase));
        }
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(
                            new SearchResponse(
                                    q.trim(),
                                    language.name(),
                                    0,
                                    List.of(),
                                    false,
                                    0,
                                    false,
                                    poolHint,
                                    hybridSuppressedByRequest,
                                    effectiveRrfK,
                                    null,
                                    debugBase));
        }
        int lim = limCap;
        var opts =
                new PerRequestSearchOptions(
                        allowHybrid,
                        rrfOverride,
                        poolOverride,
                        fuzzyEnabled,
                        minFuzzOverride,
                        explainRequested,
                        resolvedMinHybridChars,
                        embedTimeoutOverride);
        var outcome = searchRuntime.search(language, q, lim, opts);
        List<SearchHit> hits = outcome.hits();
        var reg = searchRuntime.registry(language).orElseThrow();
        List<MatchRow> rows = new ArrayList<>(hits.size());
        for (SearchHit h : hits) {
            var e = h.entry();
            rows.add(
                    new MatchRow(
                            e.code(),
                            e.level(),
                            e.description(),
                            h.score(),
                            h.matchType(),
                            HierarchyResolver.resolve(reg, e)));
        }
        OutcomeMeta m = outcome.meta();
        SearchDebugPayload debug =
                new SearchDebugPayload(
                        m.hybridSkippedShortQuery(),
                        m.embeddingFallback(),
                        m.embeddingTimedOut(),
                        m.fuzzyDisabledByRequest(),
                        m.effectiveMinFuzzyTokenLength(),
                        m.serverMinHybridQueryChars());
        return ResponseEntity.ok(
                new SearchResponse(
                        q.trim(),
                        language.name(),
                        rows.size(),
                        rows,
                        outcome.fuzzyEnabled(),
                        outcome.fuzzyTerms(),
                        outcome.hybridEnabled(),
                        outcome.candidatePool(),
                        hybridSuppressedByRequest,
                        effectiveRrfK,
                        m.explain(),
                        debug));
    }

    private static int clamp(int v, int min, int max) {
        return Math.min(max, Math.max(min, v));
    }
}
