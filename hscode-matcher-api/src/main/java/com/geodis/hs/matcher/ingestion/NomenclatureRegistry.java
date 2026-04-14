package com.geodis.hs.matcher.ingestion;

import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.domain.Language;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Immutable in-memory HS/CN nomenclature for one language (registry keys: 2/4/6/8/10 digits). */
public final class NomenclatureRegistry {

    private final Language language;
    private final Map<String, HsEntry> byCode;

    public NomenclatureRegistry(Language language, Map<String, HsEntry> byCode) {
        this.language = language;
        this.byCode = byCode;
    }

    public Language language() {
        return language;
    }

    public Optional<HsEntry> get(String code) {
        return Optional.ofNullable(byCode.get(code));
    }

    public Collection<HsEntry> entries() {
        return byCode.values();
    }

    public int size() {
        return byCode.size();
    }

    public long countAtLevel(int level) {
        return byCode.values().stream().filter(e -> e.level() == level).count();
    }
}
