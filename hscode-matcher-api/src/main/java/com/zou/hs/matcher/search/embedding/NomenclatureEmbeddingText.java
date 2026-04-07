package com.zou.hs.matcher.search.embedding;

import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;

/**
 * Text fed into {@link EmbeddingEngine#embed(String)} when indexing nomenclature rows (M1): HS codes
 * themselves are never embedded — only descriptions, with parent heading context for 6-digit lines.
 */
public final class NomenclatureEmbeddingText {

    private NomenclatureEmbeddingText() {}

    public static String textForEntry(NomenclatureRegistry registry, HsEntry entry) {
        if (entry.level() == 6 && entry.parentCode() != null) {
            return registry
                    .get(entry.parentCode())
                    .map(p -> p.description() + ". " + entry.description())
                    .orElse(entry.description());
        }
        return entry.description();
    }
}
