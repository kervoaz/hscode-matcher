package com.zou.hs.matcher.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class SearchResultTest {

    @Test
    void holdsEntryScoresAndHierarchy() {
        HsEntry chapter = new HsEntry("87", 2, "Vehicles", Language.EN, null);
        HsEntry heading = new HsEntry("8703", 4, "Motor cars", Language.EN, "87");
        HsEntry entry = new HsEntry("870321", 6, "Of cylinder capacity <= 1000cc", Language.EN, "8703");
        HierarchyContext hierarchy = new HierarchyContext(chapter, heading, List.of());

        SearchResult r = new SearchResult(entry, 0.91, 12.3, 0.88, hierarchy);

        assertThat(r.entry()).isSameAs(entry);
        assertThat(r.hybridScore()).isEqualTo(0.91);
        assertThat(r.bm25Score()).isEqualTo(12.3);
        assertThat(r.cosineScore()).isEqualTo(0.88);
        assertThat(r.hierarchy()).isSameAs(hierarchy);
    }
}
