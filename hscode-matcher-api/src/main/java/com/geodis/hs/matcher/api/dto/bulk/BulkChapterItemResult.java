package com.geodis.hs.matcher.api.dto.bulk;

import com.geodis.hs.matcher.classify.ChapterCandidate;
import java.util.List;

/** One classified row: Lucene chapter + placeholders for future LLM comparison. */
public record BulkChapterItemResult(
        String id,
        String description,
        String chapterLucene,
        String chapterTitleLucene,
        double confidenceLucene,
        List<ChapterCandidate> top3Lucene,
        boolean ambiguousLucene,
        boolean lowInformationLucene,
        String errorCodeLucene,
        String chapterLlm,
        Double confidenceLlm,
        String top3ChaptersLlm,
        Boolean ambiguousLlm,
        String errorLlm,
        String rationaleLlm,
        long latencyMsLlm,
        Boolean agreeChapter,
        long latencyMsLucene) {

    public BulkChapterItemResult {
        top3Lucene = top3Lucene == null ? List.of() : List.copyOf(top3Lucene);
    }

    /** Same classification outcome for another row id / verbatim description (bulk deduplication). */
    public BulkChapterItemResult forRow(String id, String description) {
        return new BulkChapterItemResult(
                id == null ? "" : id,
                description == null ? "" : description,
                chapterLucene(),
                chapterTitleLucene(),
                confidenceLucene(),
                top3Lucene(),
                ambiguousLucene(),
                lowInformationLucene(),
                errorCodeLucene(),
                chapterLlm(),
                confidenceLlm(),
                top3ChaptersLlm(),
                ambiguousLlm(),
                errorLlm(),
                rationaleLlm(),
                latencyMsLlm(),
                agreeChapter(),
                latencyMsLucene());
    }
}
