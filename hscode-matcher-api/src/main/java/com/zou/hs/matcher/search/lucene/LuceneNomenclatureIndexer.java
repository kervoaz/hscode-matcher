package com.zou.hs.matcher.search.lucene;

import com.zou.hs.matcher.domain.HsEntry;
import com.zou.hs.matcher.ingestion.NomenclatureRegistry;
import java.io.IOException;
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
                doc.add(new TextField("description", e.description(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
        }
        return dir;
    }
}
