package com.zou.hs.matcher.search.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.zou.hs.matcher.domain.Language;
import org.junit.jupiter.api.Test;

class EmbeddingStoreTest {

    @Test
    void uninitialized_returnsNull() {
        EmbeddingStore store = new EmbeddingStore();
        assertThat(store.matrix(Language.FR)).isNull();
        assertThat(store.codes(Language.FR)).isNull();
        assertThat(store.isInitialized(Language.FR)).isFalse();
    }

    @Test
    void initialize_and_retrieve() {
        EmbeddingStore store = new EmbeddingStore();
        float[][] m = {{1f, 0f}, {0f, 1f}};
        String[] codes = {"A", "B"};
        store.initialize(Language.EN, m, codes);
        assertThat(store.matrix(Language.EN)).isSameAs(m);
        assertThat(store.codes(Language.EN)).isSameAs(codes);
        assertThat(store.isInitialized(Language.EN)).isTrue();
    }

    @Test
    void initialize_twice_replacesMatrix() {
        EmbeddingStore store = new EmbeddingStore();
        float[][] first = {{1f}};
        float[][] second = {{2f}};
        store.initialize(Language.DE, first, new String[] {"x"});
        assertThat(store.matrix(Language.DE)).isSameAs(first);
        store.initialize(Language.DE, second, new String[] {"y"});
        assertThat(store.matrix(Language.DE)).isSameAs(second);
        assertThat(store.codes(Language.DE)).containsExactly("y");
    }
}
