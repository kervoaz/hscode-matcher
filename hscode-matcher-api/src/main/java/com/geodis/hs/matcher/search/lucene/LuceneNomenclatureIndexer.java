package com.geodis.hs.matcher.search.lucene;

import com.geodis.hs.matcher.domain.HsEntry;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

public final class LuceneNomenclatureIndexer {

    private LuceneNomenclatureIndexer() {}

    /**
     * Text indexed for lexical search: descriptions from the chapter down to {@code entry}
     * (ancestors then node), non-empty lines only. Official line shown in API remains {@link
     * HsEntry#description()} on the registry entry.
     */
    static String lexicalSearchText(NomenclatureRegistry registry, HsEntry entry) {
        List<HsEntry> leafToRoot = new ArrayList<>();
        HsEntry cur = entry;
        Set<String> guard = new HashSet<>();
        while (cur != null && guard.add(cur.code())) {
            leafToRoot.add(cur);
            String pc = cur.parentCode();
            cur = pc == null ? null : registry.get(pc).orElse(null);
        }
        Collections.reverse(leafToRoot);
        return leafToRoot.stream()
                .map(HsEntry::description)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Builds a fresh in-memory Lucene index for one language. Caller owns the returned {@link
     * Directory} and must close it.
     */
    public static Directory build(NomenclatureRegistry registry, Analyzer analyzer) throws IOException {
        ByteBuffersDirectory dir = new ByteBuffersDirectory();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setSimilarity(new BM25Similarity());
        try (IndexWriter writer = new IndexWriter(dir, iwc)) {
            for (HsEntry e : registry.entries()) {
                Document doc = new Document();
                doc.add(new StringField("code", e.code(), Field.Store.YES));
                doc.add(new IntPoint("level", e.level()));
                doc.add(new StoredField("level", e.level()));
                doc.add(
                        new TextField(
                                "description", lexicalSearchText(registry, e), Field.Store.NO));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return dir;
    }
}
