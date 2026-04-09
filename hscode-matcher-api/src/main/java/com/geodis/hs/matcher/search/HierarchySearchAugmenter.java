package com.geodis.hs.matcher.search;

import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enriches lexical search hits with {@linkplain #PARENT_CONTEXT parent category rows} so clients
 * always see chapter and heading lines when a subheading matches, even if those parents did not
 * match the query text directly.
 */
public final class HierarchySearchAugmenter {

    /** {@link MatchRow#matchType()} for a chapter or heading inferred from a descendant hit. */
    public static final String PARENT_CONTEXT = "PARENT_CONTEXT";

    /**
     * Maximum number of distinct parent codes to add beyond direct lexical hits (after score-based
     * prioritization).
     */
    public static final int MAX_PARENT_CONTEXT_ROWS = 40;

    private HierarchySearchAugmenter() {}

    /**
     * Returns a new list: all {@code lexicalRows} plus deduplicated ancestor rows. Parent scores
     * are the max lexical score among their matching descendants. Rows are sorted by descending
     * score, then code.
     */
    public static List<MatchRow> withParentCategories(
            NomenclatureRegistry registry, List<MatchRow> lexicalRows) {
        if (lexicalRows.isEmpty()) {
            return List.of();
        }
        Set<String> directCodes = new HashSet<>();
        for (MatchRow r : lexicalRows) {
            directCodes.add(r.code());
        }
        Map<String, Double> ancestorScores = new HashMap<>();
        for (MatchRow r : lexicalRows) {
            registry.get(r.code()).ifPresent(e -> accumulateAncestorScores(registry, e, r.score(), ancestorScores));
        }
        List<Map.Entry<String, Double>> parentEntries = new ArrayList<>();
        for (Map.Entry<String, Double> e : ancestorScores.entrySet()) {
            if (!directCodes.contains(e.getKey())) {
                parentEntries.add(e);
            }
        }
        parentEntries.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        if (parentEntries.size() > MAX_PARENT_CONTEXT_ROWS) {
            parentEntries = parentEntries.subList(0, MAX_PARENT_CONTEXT_ROWS);
        }
        List<MatchRow> parents = new ArrayList<>(parentEntries.size());
        for (Map.Entry<String, Double> e : parentEntries) {
            registry.get(e.getKey()).ifPresent(ent -> parents.add(toParentRow(registry, ent, e.getValue())));
        }
        List<MatchRow> merged = new ArrayList<>(lexicalRows.size() + parents.size());
        merged.addAll(lexicalRows);
        merged.addAll(parents);
        merged.sort(
                Comparator.comparingDouble(MatchRow::score)
                        .reversed()
                        .thenComparing(MatchRow::code));
        return List.copyOf(merged);
    }

    private static void accumulateAncestorScores(
            NomenclatureRegistry registry, HsEntry entry, double score, Map<String, Double> out) {
        String p = entry.parentCode();
        while (p != null) {
            out.merge(p, score, Math::max);
            HsEntry parent = registry.get(p).orElse(null);
            if (parent == null) {
                break;
            }
            p = parent.parentCode();
        }
    }

    private static MatchRow toParentRow(NomenclatureRegistry registry, HsEntry entry, double score) {
        return new MatchRow(
                entry.code(),
                entry.level(),
                entry.description(),
                score,
                PARENT_CONTEXT,
                HierarchyResolver.resolve(registry, entry));
    }
}
