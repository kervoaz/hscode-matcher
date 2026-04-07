package com.zou.hs.matcher.search.embedding;

import com.zou.hs.matcher.domain.Language;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingStore {

    private record Snapshot(float[][] matrix, String[] codes) {}

    private final Map<Language, AtomicReference<Snapshot>> stores = new EnumMap<>(Language.class);

    public EmbeddingStore() {
        for (Language lang : Language.values()) {
            stores.put(lang, new AtomicReference<>(null));
        }
    }

    public void initialize(Language lang, float[][] matrix, String[] codes) {
        stores.get(lang).set(new Snapshot(matrix, codes));
    }

    public float[][] matrix(Language lang) {
        Snapshot s = stores.get(lang).get();
        return s != null ? s.matrix() : null;
    }

    public String[] codes(Language lang) {
        Snapshot s = stores.get(lang).get();
        return s != null ? s.codes() : null;
    }

    public boolean isInitialized(Language lang) {
        return stores.get(lang).get() != null;
    }
}
