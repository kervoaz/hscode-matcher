package com.zou.hs.matcher.search.lucene;

import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import com.zou.hs.matcher.search.LexicalHit;
import com.zou.hs.matcher.search.LexicalSearchOutcome;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 * BM25 ({@link SimpleQueryParser}) OR per-token {@link FuzzyQuery}. The fuzzy side is {@linkplain
 * BoostQuery boosted} so edits still influence ranking when BM25 also fires.
 */
public final class LuceneLexicalSearchService {

    private static final int MAX_QUERY_LENGTH = 512;
    private static final int FUZZY_MAX_EXPANSIONS = 120;
    private static final float FUZZY_BOOST = 4.0f;

    private final Map<Language, LanguageSearchIndex> indexes;

    public LuceneLexicalSearchService(Map<Language, LanguageSearchIndex> indexes) {
        this.indexes = Map.copyOf(indexes);
    }

    public boolean isReady(Language language) {
        return indexes.containsKey(language);
    }

    public Optional<NomenclatureRegistry> registry(Language language) {
        LanguageSearchIndex idx = indexes.get(language);
        return idx == null ? Optional.empty() : Optional.of(idx.registry());
    }

    public LexicalSearchOutcome search(Language language, String queryText, int limit) throws IOException {
        return search(language, queryText, limit, LexicalQueryOptions.defaults());
    }

    public LexicalSearchOutcome search(Language language, String queryText, int limit, LexicalQueryOptions opts)
            throws IOException {
        if (queryText == null || queryText.isBlank()) {
            return new LexicalSearchOutcome(List.of(), 0);
        }
        String q = queryText.trim();
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        LanguageSearchIndex idx = indexes.get(language);
        if (idx == null) {
            return new LexicalSearchOutcome(List.of(), 0);
        }
        BuiltLexicalQuery built = buildQuery(idx.analyzer(), q, opts);
        if (built.query() instanceof MatchNoDocsQuery) {
            return new LexicalSearchOutcome(List.of(), built.fuzzyTokenCount());
        }
        IndexSearcher searcher = idx.searcher();
        TopDocs top = searcher.search(built.query(), Math.max(1, limit));
        List<LexicalHit> out = new ArrayList<>();
        for (ScoreDoc sd : top.scoreDocs) {
            var doc = searcher.storedFields().document(sd.doc);
            String code = doc.get("code");
            if (code == null) {
                continue;
            }
            HsEntry entry = idx.registry().get(code).orElse(null);
            if (entry == null) {
                continue;
            }
            out.add(new LexicalHit(entry, sd.score));
            if (out.size() >= limit) {
                break;
            }
        }
        return new LexicalSearchOutcome(Collections.unmodifiableList(out), built.fuzzyTokenCount());
    }

    private static BuiltLexicalQuery buildQuery(Analyzer analyzer, String text, LexicalQueryOptions opts)
            throws IOException {
        BooleanQuery.Builder top = new BooleanQuery.Builder();
        int parts = 0;
        int fuzzyTokenCount = 0;
        if (opts.fuzzyEnabled()) {
            FuzzyPart fuzzyPart = buildFuzzyPart(analyzer, text, opts.effectiveMinFuzzyTokenLength());
            Query fuzzyQuery = fuzzyPart.query();
            fuzzyTokenCount = fuzzyPart.tokenCount();
            if (!(fuzzyQuery instanceof MatchNoDocsQuery)) {
                top.add(new BoostQuery(fuzzyQuery, FUZZY_BOOST), BooleanClause.Occur.SHOULD);
                parts++;
            }
        }

        SimpleQueryParser sqp = new SimpleQueryParser(analyzer, java.util.Map.of("description", 1.0f));
        Query parsed = sqp.parse(text);
        if (!(parsed instanceof MatchNoDocsQuery)) {
            top.add(parsed, BooleanClause.Occur.SHOULD);
            parts++;
        }

        if (parts == 0) {
            return new BuiltLexicalQuery(new MatchNoDocsQuery(), fuzzyTokenCount);
        }
        if (parts > 1) {
            top.setMinimumNumberShouldMatch(1);
        }
        return new BuiltLexicalQuery(top.build(), fuzzyTokenCount);
    }

    private record BuiltLexicalQuery(Query query, int fuzzyTokenCount) {}

    private record FuzzyPart(Query query, int tokenCount) {}

    private static FuzzyPart buildFuzzyPart(Analyzer analyzer, String text, int minTokenLength) throws IOException {
        List<FuzzyQuery> terms = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream("description", text)) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String tok = termAttr.toString();
                if (tok.length() < minTokenLength) {
                    continue;
                }
                int maxEdits = tok.length() >= 6 ? 2 : 1;
                terms.add(
                        new FuzzyQuery(
                                new Term("description", tok), maxEdits, 1, FUZZY_MAX_EXPANSIONS, true));
            }
            ts.end();
        }
        if (terms.isEmpty()) {
            Query none = new MatchNoDocsQuery();
            return new FuzzyPart(none, 0);
        }
        if (terms.size() == 1) {
            return new FuzzyPart(terms.get(0), 1);
        }
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (FuzzyQuery fq : terms) {
            b.add(fq, BooleanClause.Occur.SHOULD);
        }
        b.setMinimumNumberShouldMatch(1);
        return new FuzzyPart(b.build(), terms.size());
    }
}
