package com.zou.hs.matcher.ingestion;

import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds a {@link NomenclatureRegistry} from raw EU export rows. Rows at hierarchy 8 or 10 (CN
 * subdivisions) contribute description text to the 6-digit HS key only — API remains HS6-centric.
 */
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

        Map<String, HsEntry> map = new LinkedHashMap<>();
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

        log.info(
                "Built nomenclature registry for {}: {} entries (chapters={}, headings={}, hs6={})",
                expectedLanguage,
                map.size(),
                map.values().stream().filter(x -> x.level() == 2).count(),
                map.values().stream().filter(x -> x.level() == 4).count(),
                map.values().stream().filter(x -> x.level() == 6).count());

        return new NomenclatureRegistry(expectedLanguage, Map.copyOf(map));
    }
}
