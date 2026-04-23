package com.geodis.hs.matcher.classify;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.api.dto.MatchHierarchy;
import com.geodis.hs.matcher.api.dto.MatchRow;
import com.geodis.hs.matcher.api.dto.NodeRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChapterScoreAggregatorTest {

    @Test
    void emptyQuery() {
        ChapterAggregation a = ChapterScoreAggregator.aggregate(List.of(), "");
        assertThat(a.errorCode()).isEqualTo(ChapterAggregation.ERR_EMPTY_QUERY);
        assertThat(a.bestChapterCode()).isNull();
    }

    @Test
    void aggregatesMaxScorePerChapter() {
        MatchHierarchy h84 =
                new MatchHierarchy(new NodeRef("84", 2, "Vêtements"), new NodeRef("8401", 4, "X"), List.of());
        MatchHierarchy h62 =
                new MatchHierarchy(new NodeRef("62", 2, "Habillement"), new NodeRef("6201", 4, "Y"), List.of());
        List<MatchRow> rows =
                List.of(
                        new MatchRow("8401000000", 6, "A", 10.0, "LEXICAL", h84),
                        new MatchRow("8402000000", 6, "B", 5.0, "LEXICAL", h84),
                        new MatchRow("6201000000", 6, "C", 8.0, "LEXICAL", h62));
        ChapterAggregation a = ChapterScoreAggregator.aggregate(rows, "robe");
        assertThat(a.bestChapterCode()).isEqualTo("84");
        assertThat(a.bestChapterDescription()).isEqualTo("Vêtements");
        assertThat(a.top3()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(a.confidence01()).isBetween(0.0, 1.0);
    }

    @Test
    void ignoresParentContextRows() {
        MatchHierarchy h =
                new MatchHierarchy(new NodeRef("01", 2, "Animaux"), new NodeRef("0101", 4, "H"), List.of());
        List<MatchRow> rows =
                List.of(
                        new MatchRow("0101210000", 6, "Chevaux", 3.0, "LEXICAL", h),
                        new MatchRow("01", 2, "Animaux", 99.0, "PARENT_CONTEXT", h));
        ChapterAggregation a = ChapterScoreAggregator.aggregate(rows, "chevaux");
        assertThat(a.bestChapterCode()).isEqualTo("01");
        assertThat(a.top3().get(0).score()).isEqualTo(3.0);
    }

    @Test
    void ambiguousWhenSecondChapterCloseToFirst() {
        MatchHierarchy h1 =
                new MatchHierarchy(new NodeRef("84", 2, "A"), new NodeRef("8401", 4, "x"), List.of());
        MatchHierarchy h2 =
                new MatchHierarchy(new NodeRef("62", 2, "B"), new NodeRef("6201", 4, "y"), List.of());
        List<MatchRow> rows =
                List.of(
                        new MatchRow("8401000000", 6, "d1", 10.0, "LEXICAL", h1),
                        new MatchRow("6201000000", 6, "d2", 6.0, "LEXICAL", h2));
        ChapterAggregation a = ChapterScoreAggregator.aggregate(rows, "something long enough");
        assertThat(a.ambiguous()).isTrue();
    }
}
