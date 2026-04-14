package com.geodis.hs.matcher.api;

import com.geodis.hs.matcher.api.dto.CodeLookupResponse;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;
import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.GoodsCodes;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import com.geodis.hs.matcher.search.HierarchyResolver;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CodeLookupController {

    private final NomenclatureSearchRuntime searchRuntime;

    public CodeLookupController(NomenclatureSearchRuntime searchRuntime) {
        this.searchRuntime = searchRuntime;
    }

    /**
     * Returns the nomenclature line and hierarchy for a known registry key. {@code code} may contain
     * punctuation; digits are extracted and must form a 2-, 4-, 6-, 8-, or 10-digit key.
     */
    @GetMapping("/codes/{code}")
    public ResponseEntity<CodeLookupResponse> lookup(
            @PathVariable("code") String code,
            @RequestParam(value = "lang", defaultValue = "FR") String lang) {
        Optional<String> normalized = GoodsCodes.normalizeLookupCode(code);
        if (normalized.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Language language;
        try {
            language = Language.valueOf(lang.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (!searchRuntime.isReady(language)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        Optional<NomenclatureRegistry> regOpt = searchRuntime.registry(language);
        if (regOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        NomenclatureRegistry registry = regOpt.get();
        Optional<HsEntry> entryOpt = registry.get(normalized.get());
        if (entryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HsEntry entry = entryOpt.get();
        var hierarchy = HierarchyResolver.resolve(registry, entry);
        var body =
                new CodeLookupResponse(
                        entry.code(),
                        entry.level(),
                        entry.description(),
                        language.name(),
                        hierarchy);
        return ResponseEntity.ok(body);
    }
}
