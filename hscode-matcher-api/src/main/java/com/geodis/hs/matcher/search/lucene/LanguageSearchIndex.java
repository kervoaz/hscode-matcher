package com.geodis.hs.matcher.search.lucene;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

/** Lucene directory + reader + searcher bound to one {@link NomenclatureRegistry}. */
public final class LanguageSearchIndex implements AutoCloseable {

    private final Language language;
    private final NomenclatureRegistry registry;
    private final Analyzer analyzer;
    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;

    public LanguageSearchIndex(
            Language language, NomenclatureRegistry registry, Analyzer analyzer, Directory directory)
            throws IOException {
        this.language = language;
        this.registry = registry;
        this.analyzer = analyzer;
        this.directory = directory;
        this.reader = DirectoryReader.open(directory);
        this.searcher = new IndexSearcher(reader);
    }

    public Language language() {
        return language;
    }

    public NomenclatureRegistry registry() {
        return registry;
    }

    public Analyzer analyzer() {
        return analyzer;
    }

    public IndexSearcher searcher() {
        return searcher;
    }

    @Override
    public void close() throws IOException {
        reader.close();
        directory.close();
        analyzer.close();
    }
}
