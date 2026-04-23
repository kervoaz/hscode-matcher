package com.geodis.hs.matcher.search;

import com.geodis.hs.matcher.config.LexicalSearchRankProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Applies HS depth boost to Lucene scores before truncation to the client limit. */
public final class LexicalRanking {

    private LexicalRanking() {}

    /** HS tiers: chapter=0, heading=1, HS6=2, CN8=3, CN10=4. */
    public static int depthTier(int level) {
        return switch (level) {
            case 2 -> 0;
            case 4 -> 1;
            case 6 -> 2;
            case 8 -> 3;
            case 10 -> 4;
            default -> 0;
        };
    }

    public static float depthMultiplier(int level, LexicalSearchRankProperties p) {
        if (p == null) {
            return 1f;
        }
        double tier = depthTier(level);
        double boost = p.getLexicalDepthBoostPerTier();
        if (boost <= 0) {
            return 1f;
        }
        return (float) (1.0 + tier * boost);
    }

    /**
     * Re-scores by depth, re-sorts descending, returns at most {@code limit} hits. {@code raw} is
     * typically over-fetched from Lucene.
     */
    public static List<LexicalHit> rerankByDepth(List<LexicalHit> raw, int limit, LexicalSearchRankProperties p) {
        if (raw.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<LexicalHit> adj = new ArrayList<>(raw.size());
        for (LexicalHit h : raw) {
            float m = depthMultiplier(h.entry().level(), p);
            adj.add(new LexicalHit(h.entry(), h.score() * m));
        }
        adj.sort(Comparator.comparingDouble(LexicalHit::score).reversed());
        if (adj.size() <= limit) {
            return List.copyOf(adj);
        }
        return List.copyOf(adj.subList(0, limit));
    }

    public static int luceneCollectCount(int clientLimit, LexicalSearchRankProperties p) {
        int lim = Math.max(1, clientLimit);
        if (p == null) {
            return lim;
        }
        int factor = Math.max(1, p.getOverfetchFactor());
        int maxCap = Math.max(lim, p.getOverfetchMax());
        int desired = lim * factor;
        return Math.max(lim, Math.min(desired, maxCap));
    }
}
