package com.geodis.hs.matcher.classify;



import com.geodis.hs.matcher.api.dto.MatchRow;

import com.geodis.hs.matcher.config.LexicalSearchRankProperties;
import com.geodis.hs.matcher.config.NomenclatureSearchRuntime;

import com.geodis.hs.matcher.domain.Language;

import com.geodis.hs.matcher.embed.BulkEmbeddingProperties;

import com.geodis.hs.matcher.embed.ChapterEmbeddingIndex;

import com.geodis.hs.matcher.embed.ChapterRefinementCandidateFusion;

import com.geodis.hs.matcher.embed.EmbeddingHttpClient;

import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;

import com.geodis.hs.matcher.search.HierarchyResolver;

import com.geodis.hs.matcher.search.HierarchySearchAugmenter;

import com.geodis.hs.matcher.search.LexicalHit;

import com.geodis.hs.matcher.search.LexicalSearchParams;

import java.io.IOException;

import java.util.ArrayList;

import java.util.List;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;



@Service

public class LuceneChapterClassifier {



    private static final Logger log = LoggerFactory.getLogger(LuceneChapterClassifier.class);



    private final NomenclatureSearchRuntime searchRuntime;

    private final BulkEmbeddingProperties embeddingProperties;

    private final EmbeddingHttpClient embeddingHttpClient;

    private final ChapterEmbeddingIndex chapterEmbeddingIndex;

    private final LexicalSearchRankProperties lexicalSearchRankProperties;

    public LuceneChapterClassifier(

            NomenclatureSearchRuntime searchRuntime,

            BulkEmbeddingProperties embeddingProperties,

            EmbeddingHttpClient embeddingHttpClient,

            ChapterEmbeddingIndex chapterEmbeddingIndex,

            LexicalSearchRankProperties lexicalSearchRankProperties) {

        this.searchRuntime = searchRuntime;

        this.embeddingProperties = embeddingProperties;

        this.embeddingHttpClient = embeddingHttpClient;

        this.chapterEmbeddingIndex = chapterEmbeddingIndex;

        this.lexicalSearchRankProperties = lexicalSearchRankProperties;

    }



    /**

     * Classifies free text into an HS chapter using the same lexical pipeline as {@code GET /search}

     * (BM25 + fuzzy), then aggregates scores by chapter from {@code LEXICAL} hits only. When {@code

     * bulk.embedding.enabled=true}, merges Lucene’s ranked chapters with semantic nearest chapters

     * (RRF) and replaces {@link ChapterAggregation#refinementCandidates()} for the LLM shortlist.

     */

    public ChapterAggregation classify(

            Language language, String description, LexicalSearchParams params, int searchLimit)

            throws IOException {

        if (!params.isRunnable()) {

            throw new IllegalArgumentException("At least one of fuzzy or bm25 must be true");

        }

        String q = description == null ? "" : description.trim();

        if (q.isEmpty()) {

            return new ChapterAggregation(

                    null,

                    null,

                    0.0,

                    List.of(),

                    true,

                    true,

                    ChapterAggregation.ERR_EMPTY_QUERY,

                    List.of(),

                    List.of());

        }

        long t0 = System.nanoTime();

        NomenclatureRegistry reg =

                searchRuntime.registry(language).orElseThrow(() -> new IllegalStateException("no registry"));

        long tSearch0 = System.nanoTime();

        var outcome = searchRuntime.search(language, q, searchLimit, params);

        long searchMs = (System.nanoTime() - tSearch0) / 1_000_000L;

        List<MatchRow> lexicalRows = new ArrayList<>();

        for (LexicalHit h : outcome.hits()) {

            var e = h.entry();

            lexicalRows.add(

                    new MatchRow(

                            e.code(),

                            e.level(),

                            e.description(),

                            h.score(),

                            "LEXICAL",

                            HierarchyResolver.resolve(reg, e)));

        }

        long tAug0 = System.nanoTime();

        List<MatchRow> rows =

                HierarchySearchAugmenter.withParentCategories(

                        reg,

                        lexicalRows,

                        lexicalSearchRankProperties.getParentContextScoreFactor());

        long augmentMs = (System.nanoTime() - tAug0) / 1_000_000L;

        long tAgg0 = System.nanoTime();

        ChapterAggregation lexical =

                ChapterScoreAggregator.aggregate(

                        rows,

                        q,

                        embeddingProperties.getRefinementCandidateCount(),

                        embeddingProperties.getLexicalRankPool());

        long aggregateMs = (System.nanoTime() - tAgg0) / 1_000_000L;

        ChapterAggregation out = maybeFuseEmbeddings(language, q, lexical);

        if (log.isDebugEnabled()) {

            long totalMs = (System.nanoTime() - t0) / 1_000_000L;

            log.debug(

                    "chapter classify breakdown lang={} qLen={} lexicalHits={} searchMs={} augmentMs={} aggregateMs={} totalMs={}",

                    language,

                    q.length(),

                    outcome.hits().size(),

                    searchMs,

                    augmentMs,

                    aggregateMs,

                    totalMs);

        }

        return out;

    }



    private ChapterAggregation maybeFuseEmbeddings(Language language, String q, ChapterAggregation lexical) {

        if (!embeddingProperties.isEnabled()) {

            return lexical;

        }

        if (lexical.errorCode() != null) {

            return lexical;

        }

        try {

            long tPath = System.nanoTime();

            chapterEmbeddingIndex.ensureBuilt(language);

            long ensureBuiltMs = (System.nanoTime() - tPath) / 1_000_000L;

            if (!chapterEmbeddingIndex.isReady(language)) {

                if (log.isDebugEnabled()) {

                    log.debug(

                            "chapter embedding path skipped lang={} reason=index_not_ready ensureBuiltMs={}",

                            language,

                            ensureBuiltMs);

                }

                return lexical;

            }

            long tEmb = System.nanoTime();

            float[] qv = embeddingHttpClient.embedOrEmpty(q);

            long embedMs = (System.nanoTime() - tEmb) / 1_000_000L;

            if (qv.length == 0) {

                if (log.isDebugEnabled()) {

                    log.debug(

                            "chapter embedding path skipped lang={} reason=empty_query_vector ensureBuiltMs={} embedMs={}",

                            language,

                            ensureBuiltMs,

                            embedMs);

                }

                return lexical;

            }

            long tNei = System.nanoTime();

            List<ChapterCandidate> sem =

                    chapterEmbeddingIndex.semanticNeighbors(

                            language, qv, embeddingProperties.getSemanticPool());

            long neighborsMs = (System.nanoTime() - tNei) / 1_000_000L;

            long tFuse = System.nanoTime();

            List<ChapterCandidate> merged =

                    ChapterRefinementCandidateFusion.fuseRrf(

                            lexical.lexicalRankedMax10(),

                            sem,

                            embeddingProperties.getRrfK(),

                            embeddingProperties.getRefinementCandidateCount());

            long fuseMs = (System.nanoTime() - tFuse) / 1_000_000L;

            if (log.isDebugEnabled()) {

                log.debug(

                        "chapter embedding fuse lang={} ensureBuiltMs={} embedMs={} neighborsMs={} fuseMs={} mergedSize={}",

                        language,

                        ensureBuiltMs,

                        embedMs,

                        neighborsMs,

                        fuseMs,

                        merged.size());

            }

            if (merged.isEmpty()) {

                return lexical;

            }

            return lexical.withRefinementCandidates(merged);

        } catch (RuntimeException e) {

            log.warn("Chapter embedding fusion skipped: {}", e.getMessage());

            return lexical;

        }

    }

}

