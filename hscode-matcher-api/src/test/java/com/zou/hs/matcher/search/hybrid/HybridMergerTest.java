package com.zou.hs.matcher.search.hybrid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HybridMergerTest {

    @Test
    void rrf_prefersCodeHighInBothLists() {
        List<String> lex = List.of("A", "B", "C");
        List<String> sem = List.of("A", "D", "E");
        List<HybridMerger.CodeRrf> out = HybridMerger.rrfMerge(lex, sem, 60);
        assertThat(out.get(0).code()).isEqualTo("A");
        assertThat(out.get(0).rawRrf()).isGreaterThan(out.get(1).rawRrf());
    }

    @Test
    void rrf_singleList_stillRanks() {
        List<String> lex = List.of("X", "Y");
        List<HybridMerger.CodeRrf> out = HybridMerger.rrfMerge(lex, List.of(), 10);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).code()).isEqualTo("X");
    }
}
