package com.geodis.hs.matcher.config;

import com.geodis.hs.matcher.domain.Language;
import com.geodis.hs.matcher.ingestion.NomenclatureRegistry;
import com.geodis.hs.matcher.search.LexicalSearchOutcome;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Holds the current {@link NomenclatureIndexBundle} and supports atomic reload. Reads use the
 * bundle snapshot without locking so searches are not blocked during rebuild.
 */
@Service
public class NomenclatureSearchRuntime implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NomenclatureSearchRuntime.class);

    private final NomenclatureCsvProperties csvProperties;
    private final AtomicReference<NomenclatureIndexBundle> bundleRef;

    public NomenclatureSearchRuntime(NomenclatureCsvProperties csvProperties) throws IOException {
        this.csvProperties = csvProperties;
        this.bundleRef = new AtomicReference<>(NomenclatureIndexBundle.load(csvProperties));
    }

    public boolean isReady(Language language) {
        NomenclatureIndexBundle b = bundleRef.get();
        return b != null && b.lexicalSearchService().isReady(language);
    }

    public Optional<NomenclatureRegistry> registry(Language language) {
        NomenclatureIndexBundle b = bundleRef.get();
        return b == null ? Optional.empty() : b.lexicalSearchService().registry(language);
    }

    public LexicalSearchOutcome search(Language language, String queryText, int limit) throws IOException {
        NomenclatureIndexBundle b = bundleRef.get();
        if (b == null) {
            return new LexicalSearchOutcome(List.of(), 0);
        }
        return b.lexicalSearchService().search(language, queryText, limit);
    }

    public boolean anyLanguageReady() {
        NomenclatureIndexBundle b = bundleRef.get();
        return b != null && b.anyLanguageReady();
    }

    /**
     * Rebuilds all configured language indexes from CSV paths in {@link NomenclatureCsvProperties}.
     * On failure the previous bundle remains active.
     */
    public synchronized void reload() throws IOException {
        NomenclatureIndexBundle next = NomenclatureIndexBundle.load(csvProperties);
        NomenclatureIndexBundle prev = bundleRef.getAndSet(next);
        if (prev != null) {
            prev.close();
        }
        log.info("Nomenclature reload complete (anyLanguageReady={})", next.anyLanguageReady());
    }

    @Override
    public void destroy() {
        NomenclatureIndexBundle b = bundleRef.getAndSet(null);
        if (b != null) {
            b.close();
        }
    }
}
