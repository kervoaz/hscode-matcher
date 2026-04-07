package com.zou.hs.matcher.domain;

import java.util.List;
import java.util.Objects;

/**
 * Chapter and heading context for a matched entry, plus sibling subheadings when applicable.
 *
 * @param siblings never {@code null}; use {@link List#of()} when there are no siblings
 */
public record HierarchyContext(HsEntry chapter, HsEntry heading, List<HsEntry> siblings) {

    public HierarchyContext {
        Objects.requireNonNull(chapter, "chapter");
        Objects.requireNonNull(heading, "heading");
        siblings = siblings == null ? List.of() : List.copyOf(siblings);
    }
}
