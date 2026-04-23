package com.geodis.hs.matcher.api.dto.bulk;

import static org.assertj.core.api.Assertions.assertThat;

import com.geodis.hs.matcher.classify.ChapterCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BulkChapterItemResultTest {

    @Test
    void forRow_remapsIdAndDescription() {
        BulkChapterItemResult a =
                new BulkChapterItemResult(
                        "old-id",
                        "old desc",
                        "84",
                        "T",
                        0.9,
                        List.of(new ChapterCandidate("84", "T", 1)),
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0L,
                        null,
                        10L);
        BulkChapterItemResult b = a.forRow("new-id", "new desc");
        assertThat(b.id()).isEqualTo("new-id");
        assertThat(b.description()).isEqualTo("new desc");
        assertThat(b.chapterLucene()).isEqualTo("84");
        assertThat(b.latencyMsLucene()).isEqualTo(10L);
    }
}
