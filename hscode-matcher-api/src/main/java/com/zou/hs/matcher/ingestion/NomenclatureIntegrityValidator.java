package com.zou.hs.matcher.ingestion;

import com.zou.hs.matcher.domain.HsEntry;

/**
 * Sanity checks after building a {@link NomenclatureRegistry} (parent links, rough EU export
 * counts).
 */
public final class NomenclatureIntegrityValidator {

    public record Expectations(int minChapters, int maxChapters, int minHeadings, int maxHeadings, int minHs6, int maxHs6) {
        /** Ballpark ranges for full EU CIRCABC nomenclature export (~25k rows per language). */
        public static Expectations euCircabcFullExport() {
            return new Expectations(90, 110, 1100, 1450, 5000, 9500);
        }
    }

    private NomenclatureIntegrityValidator() {}

    public static void validate(NomenclatureRegistry registry, Expectations exp) {
        long chapters = registry.countAtLevel(2);
        long headings = registry.countAtLevel(4);
        long hs6 = registry.countAtLevel(6);
        if (chapters < exp.minChapters() || chapters > exp.maxChapters()) {
            throw new NomenclatureIngestException(
                    "Chapter count out of expected range [" + exp.minChapters() + "," + exp.maxChapters() + "]: " + chapters);
        }
        if (headings < exp.minHeadings() || headings > exp.maxHeadings()) {
            throw new NomenclatureIngestException(
                    "Heading count out of expected range [" + exp.minHeadings() + "," + exp.maxHeadings() + "]: " + headings);
        }
        if (hs6 < exp.minHs6() || hs6 > exp.maxHs6()) {
            throw new NomenclatureIngestException(
                    "HS6 row count out of expected range [" + exp.minHs6() + "," + exp.maxHs6() + "]: " + hs6);
        }

        for (HsEntry e : registry.entries()) {
            if (e.level() == 2) {
                if (e.parentCode() != null) {
                    throw new NomenclatureIngestException("Chapter " + e.code() + " must have null parent");
                }
                continue;
            }
            if (e.parentCode() == null) {
                throw new NomenclatureIngestException("Entry " + e.code() + " (level " + e.level() + ") missing parent");
            }
            HsEntry parent =
                    registry.get(e.parentCode())
                            .orElseThrow(
                                    () -> new NomenclatureIngestException("Missing parent " + e.parentCode() + " for " + e.code()));
            int expectParentLevel = e.level() == 4 ? 2 : 4;
            if (parent.level() != expectParentLevel) {
                throw new NomenclatureIngestException(
                        "Wrong parent level for "
                                + e.code()
                                + ": expected parent level "
                                + expectParentLevel
                                + " but "
                                + parent.code()
                                + " is level "
                                + parent.level());
            }
        }
    }
}
