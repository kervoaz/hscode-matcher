package com.geodis.hs.matcher.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LexicalSearchParamsTest {

    @Test
    void mergeUsesDefaultsForNulls() {
        LexicalSearchParams p = LexicalSearchParams.merge(null, null, null, null, null, null, null, null, null);
        assertThat(p).isEqualTo(LexicalSearchParams.DEFAULT.normalized());
    }

    @Test
    void normalizedClampsValues() {
        LexicalSearchParams raw =
                new LexicalSearchParams(true, true, 0.01f, 99, 9999, 9, 9, 9, 9999);
        LexicalSearchParams n = raw.normalized();
        assertThat(n.fuzzyBoost()).isEqualTo(0.25f);
        assertThat(n.minFuzzyTokenLength()).isEqualTo(20);
        assertThat(n.fuzzyMaxExpansions()).isEqualTo(500);
        assertThat(n.fuzzyPrefixLength()).isEqualTo(2);
        assertThat(n.maxEditsShort()).isEqualTo(2);
        assertThat(n.maxEditsLong()).isEqualTo(2);
        assertThat(n.maxQueryChars()).isEqualTo(512);
    }
}
