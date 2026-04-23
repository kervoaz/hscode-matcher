package com.geodis.hs.matcher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Post-retrieval ranking tweaks for lexical search: HS depth and parent-context rows.
 *
 * <p>Indexed text is {@linkplain com.geodis.hs.matcher.search.lucene.LuceneNomenclatureIndexer
 * ancestors + node}; shallow nodes can get inflated BM25 vs long concatenated leaves. Boosting by
 * {@link #lexicalDepthBoostPerTier} favors matches on more specific nomenclature lines.
 */
@ConfigurationProperties(prefix = "nomenclature.search.ranking")
public class LexicalSearchRankProperties {

    /**
     * Per HS tier above chapter: multiplier contribution {@code 1 + tierIndex * this}, where tier
     * is 0 (chapter), 1 (heading), 2 (HS6), 3 (CN8), 4 (CN10).
     */
    private double lexicalDepthBoostPerTier = 0.06;

    /**
     * Score multiplier for {@link com.geodis.hs.matcher.search.HierarchySearchAugmenter#PARENT_CONTEXT}
     * rows so a direct lexical hit on a code ranks above the same code inferred from descendants.
     */
    private double parentContextScoreFactor = 0.88;

    /** Lucene collects this many hits before depth re-ranking, as a multiple of the requested limit. */
    private int overfetchFactor = 5;

    /** Cap on Lucene collect count (cost control). */
    private int overfetchMax = 200;

    public double getLexicalDepthBoostPerTier() {
        return lexicalDepthBoostPerTier;
    }

    public void setLexicalDepthBoostPerTier(double lexicalDepthBoostPerTier) {
        this.lexicalDepthBoostPerTier = lexicalDepthBoostPerTier;
    }

    public double getParentContextScoreFactor() {
        return parentContextScoreFactor;
    }

    public void setParentContextScoreFactor(double parentContextScoreFactor) {
        this.parentContextScoreFactor = parentContextScoreFactor;
    }

    public int getOverfetchFactor() {
        return overfetchFactor;
    }

    public void setOverfetchFactor(int overfetchFactor) {
        this.overfetchFactor = overfetchFactor;
    }

    public int getOverfetchMax() {
        return overfetchMax;
    }

    public void setOverfetchMax(int overfetchMax) {
        this.overfetchMax = overfetchMax;
    }
}
