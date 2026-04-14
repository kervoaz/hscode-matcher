package com.geodis.hs.matcher.search;

import com.geodis.hs.matcher.api.dto.MatchHierarchy;
import com.geodis.hs.matcher.api.dto.NodeRef;
import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Derives chapter / heading / sibling HS6 lines from the in-memory registry (code prefixes are
 * ground truth).
 */
public final class HierarchyResolver {

    /** Cap peer subheadings per hit to keep payloads small. */
    public static final int MAX_SIBLING_SUBHEADINGS = 30;

    private HierarchyResolver() {}

    public static MatchHierarchy resolve(NomenclatureRegistry registry, HsEntry entry) {
        return switch (entry.level()) {
            case 2 -> new MatchHierarchy(ref(entry), null, List.of());
            case 4 -> resolveHeading(registry, entry);
            case 6 -> resolveSubheading(registry, entry);
            case 8, 10 -> resolveCnSubdivision(registry, entry);
            default -> new MatchHierarchy(null, null, List.of());
        };
    }

    /**
     * CN8 / CN10 hits: same chapter, heading, and HS6 peer list as the HS6 prefix of the code (code
     * prefixes are ground truth).
     */
    private static MatchHierarchy resolveCnSubdivision(NomenclatureRegistry registry, HsEntry entry) {
        String code = entry.code();
        if (code.length() < 6) {
            return new MatchHierarchy(null, null, List.of());
        }
        String six = code.substring(0, 6);
        return resolveSubheadingContext(registry, six);
    }

    private static MatchHierarchy resolveHeading(NomenclatureRegistry registry, HsEntry heading) {
        NodeRef chapter =
                heading.parentCode() == null
                        ? null
                        : registry.get(heading.parentCode()).map(HierarchyResolver::ref).orElse(null);
        List<NodeRef> children = listSubheadingsUnderHeading(registry, heading.code(), null);
        return new MatchHierarchy(chapter, ref(heading), children);
    }

    private static MatchHierarchy resolveSubheading(NomenclatureRegistry registry, HsEntry sub) {
        if (sub.parentCode() == null) {
            return new MatchHierarchy(null, null, List.of());
        }
        return resolveSubheadingContext(registry, sub.code());
    }

    /** {@code sixDigitCode} must be a 6-character HS subheading key. */
    private static MatchHierarchy resolveSubheadingContext(NomenclatureRegistry registry, String sixDigitCode) {
        if (sixDigitCode.length() < 6) {
            return new MatchHierarchy(null, null, List.of());
        }
        HsEntry sub = registry.get(sixDigitCode).orElse(null);
        String headingCode =
                sub != null && sub.parentCode() != null ? sub.parentCode() : sixDigitCode.substring(0, 4);
        HsEntry heading = registry.get(headingCode).orElse(null);
        NodeRef headingRef = heading == null ? null : ref(heading);
        NodeRef chapterRef = null;
        if (heading != null && heading.parentCode() != null) {
            chapterRef = registry.get(heading.parentCode()).map(HierarchyResolver::ref).orElse(null);
        }
        if (chapterRef == null) {
            chapterRef = registry.get(sixDigitCode.substring(0, 2)).map(HierarchyResolver::ref).orElse(null);
        }
        List<NodeRef> siblings = listSubheadingsUnderHeading(registry, headingCode, sixDigitCode);
        return new MatchHierarchy(chapterRef, headingRef, siblings);
    }

    private static List<NodeRef> listSubheadingsUnderHeading(
            NomenclatureRegistry registry, String headingCode, String excludeSixDigitCode) {
        List<HsEntry> list = new ArrayList<>();
        for (HsEntry e : registry.entries()) {
            if (e.level() != 6 || !headingCode.equals(e.parentCode())) {
                continue;
            }
            if (excludeSixDigitCode != null && excludeSixDigitCode.equals(e.code())) {
                continue;
            }
            list.add(e);
        }
        list.sort(Comparator.comparing(HsEntry::code));
        if (list.size() > MAX_SIBLING_SUBHEADINGS) {
            list = list.subList(0, MAX_SIBLING_SUBHEADINGS);
        }
        return list.stream().map(HierarchyResolver::ref).toList();
    }

    private static NodeRef ref(HsEntry e) {
        return new NodeRef(e.code(), e.level(), e.description());
    }
}
