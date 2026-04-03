package com.geodis.hs.matcher.api;

import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.api.dto.SearchResponse;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.search.HierarchyResolver;
import com.geodis.hs.matcher.search.LexicalHit;
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

    private final NomenclatureSearchRuntime searchRuntime;

    public SearchController(NomenclatureSearchRuntime searchRuntime) {
        this.searchRuntime = searchRuntime;
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam("q") String q,
            @RequestParam(value = "lang", defaultValue = "FR") String lang,
            @RequestParam(value = "limit", defaultValue = "10") int limit)
            throws IOException {
        if (q == null || q.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse("", lang, 0, List.of(), false, 0));
        }
        Language language;
        try {
            language = Language.valueOf(lang.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new SearchResponse(q.trim(), lang, 0, List.of(), false, 0));
        }
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new SearchResponse(q.trim(), language.name(), 0, List.of(), false, 0));
        }
        int lim = Math.min(Math.max(1, limit), MAX_LIMIT);
        var outcome = searchRuntime.search(language, q, lim);
        List<LexicalHit> hits = outcome.hits();
        var reg = searchRuntime.registry(language).orElseThrow();
        List<MatchRow> rows = new ArrayList<>(hits.size());
        for (LexicalHit h : hits) {
            var e = h.entry();
            rows.add(
                    new MatchRow(
                            e.code(),
                            e.level(),
                            e.description(),
                            h.score(),
                            "LEXICAL",
                            HierarchyResolver.resolve(reg, e)));
        }
        return ResponseEntity.ok(
                new SearchResponse(
                        q.trim(),
                        language.name(),
                        rows.size(),
                        rows,
                        outcome.fuzzyEnabled(),
                        outcome.fuzzyTerms()));
    }
}
