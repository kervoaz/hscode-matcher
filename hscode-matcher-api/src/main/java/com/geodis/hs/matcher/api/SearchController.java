package com.geodis.hs.matcher.api;

import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.api.dto.SearchResponse;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.config.LexicalSearchRankProperties;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.search.HierarchyResolver;
import com.geodis.hs.matcher.search.HierarchySearchAugmenter;
import com.geodis.hs.matcher.search.LexicalHit;
import com.geodis.hs.matcher.search.LexicalSearchParams;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class SearchController {

    private static final int MAX_LIMIT = 50;

    private final NomenclatureSearchRuntime searchRuntime;
    private final LexicalSearchRankProperties lexicalSearchRankProperties;

    public SearchController(
            NomenclatureSearchRuntime searchRuntime, LexicalSearchRankProperties lexicalSearchRankProperties) {
        this.searchRuntime = searchRuntime;
        this.lexicalSearchRankProperties = lexicalSearchRankProperties;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "lang", defaultValue = "FR") String lang,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
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
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse("", lang, 0, List.of(), false, 0, tuning));
        }
        Language language;
        try {
            language = Language.valueOf(lang.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse(q.trim(), lang, 0, List.of(), false, 0, tuning));
        }
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SearchResponse(q.trim(), language.name(), 0, List.of(), false, 0, tuning));
        }
        int lim = Math.min(Math.max(1, limit), MAX_LIMIT);
        var outcome = searchRuntime.search(language, q, lim, tuning);
        List<LexicalHit> hits = outcome.hits();
        var reg = searchRuntime.registry(language).orElseThrow();
        List<MatchRow> lexicalRows = new ArrayList<>(hits.size());
        for (LexicalHit h : hits) {
            var e = h.entry();
            lexicalRows.add(
                    new MatchRow(
                            e.code(),
                            e.level(),
                            e.description(),
                            h.score(),
                            "LEXICAL",
                            HierarchyResolver.resolve(reg, e)));
        }
        List<MatchRow> rows =
                HierarchySearchAugmenter.withParentCategories(
                        reg, lexicalRows, lexicalSearchRankProperties.getParentContextScoreFactor());
        return ResponseEntity.ok(
                new SearchResponse(
                        q.trim(),
                        language.name(),
                        rows.size(),
                        rows,
                        outcome.fuzzyEnabled(),
                        outcome.fuzzyTerms(),
                        tuning));
    }
}
