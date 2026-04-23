package com.geodis.hs.matcher.api.dto.bulk;

import com.geodis.hs.matcher.search.LexicalSearchParams;
import java.util.List;

public record BulkChapterClassifyResponse(
        String runId,
        String lang,
        String lucenePipeline,
        LexicalSearchParams tuning,
        List<BulkChapterItemResult> items,
        BulkChapterSummary summary,
        /** Absolute path of the written JSON file when {@code bulk.chapter-classify.output-dir} is set. */
        String outputFile) {

    public BulkChapterClassifyResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
