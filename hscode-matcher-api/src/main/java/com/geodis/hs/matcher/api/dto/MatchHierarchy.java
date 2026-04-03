package com.geodis.hs.matcher.api.dto;

import java.util.List;

/**
 * Parent chapter and heading for a hit, plus optional peer HS6 codes under the same 4-digit heading.
 */
public record MatchHierarchy(NodeRef chapter, NodeRef heading, List<NodeRef> siblingSubheadings) {

    public MatchHierarchy {
        siblingSubheadings = siblingSubheadings == null ? List.of() : List.copyOf(siblingSubheadings);
    }
}
