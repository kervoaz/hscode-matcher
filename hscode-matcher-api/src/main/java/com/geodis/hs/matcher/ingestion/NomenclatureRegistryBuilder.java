package com.geodis.hs.matcher.ingestion;

import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.domain.Language;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds a {@link NomenclatureRegistry} from raw EU export rows (separate entries per hierarchy level). */
public final class NomenclatureRegistryBuilder {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureRegistryBuilder.class);

    private NomenclatureRegistryBuilder() {}

    public static NomenclatureRegistry build(List<RawNomenclatureRow> rows, Language expectedLanguage) {
        LinkedHashMap<String, List<String>> descriptionsByKey = new LinkedHashMap<>();
        int rowIndex = 0;
        for (RawNomenclatureRow r : rows) {
            rowIndex++;
            if (r.language() != expectedLanguage) {
                throw new NomenclatureIngestException(
                        "Row " + rowIndex + " language " + r.language() + " does not match expected " + expectedLanguage);
            }
            String key = GoodsCodes.hsKey(r.hierarchyPosition(), r.tenDigitGoodsCode());
            descriptionsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(r.description());
        }

        LinkedHashMap<String, HsEntry> map = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : descriptionsByKey.entrySet()) {
            String key = e.getKey();
            if (!GoodsCodes.isValidHsKey(key)) {
                throw new NomenclatureIngestException("Internal error: invalid HS key " + key);
            }
            int level = GoodsCodes.levelFromKeyLength(key);
            String parent = GoodsCodes.parentKey(key, level);
            String description =
                    e.getValue().stream()
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.joining("\n"));
            map.put(key, new HsEntry(key, level, description, expectedLanguage, parent));
        }

        ensureStructuralParents(map, expectedLanguage);

        log.info(
                "Built nomenclature registry for {}: {} entries (chapters={}, headings={}, hs6={}, cn8={}, cn10={})",
                expectedLanguage,
                map.size(),
                map.values().stream().filter(x -> x.level() == 2).count(),
                map.values().stream().filter(x -> x.level() == 4).count(),
                map.values().stream().filter(x -> x.level() == 6).count(),
                map.values().stream().filter(x -> x.level() == 8).count(),
                map.values().stream().filter(x -> x.level() == 10).count());

        return new NomenclatureRegistry(expectedLanguage, Map.copyOf(map));
    }

    /**
     * EU CIRCABC rows sometimes skip an HS6 (or CN8) line while still emitting CN subdivisions. The
     * registry still needs each {@link HsEntry#parentCode()} present for integrity and hierarchy.
     * Missing keys are added with an empty description (search hits only on rows with text).
     */
    private static void ensureStructuralParents(Map<String, HsEntry> map, Language language) {
        for (;;) {
            List<String> missing = new ArrayList<>();
            for (HsEntry e : map.values()) {
                String p = e.parentCode();
                if (p != null && !map.containsKey(p)) {
                    missing.add(p);
                }
            }
            if (missing.isEmpty()) {
                break;
            }
            for (String p : missing) {
                if (map.containsKey(p)) {
                    continue;
                }
                if (!GoodsCodes.isValidHsKey(p)) {
                    throw new NomenclatureIngestException("Cannot synthesize invalid parent key: " + p);
                }
                int lvl = GoodsCodes.levelFromKeyLength(p);
                String pp = GoodsCodes.parentKey(p, lvl);
                map.put(p, new HsEntry(p, lvl, "", language, pp));
            }
        }
    }
}
