package com.zou.hs.matcher.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HsEntryTest {

    @Test
    void equals_sameCodeLevelDescriptionLanguageParent() {
        HsEntry a =
                new HsEntry("010121", 6, "Live horses", Language.EN, "0101");
        HsEntry b =
                new HsEntry("010121", 6, "Live horses", Language.EN, "0101");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void accessors() {
        HsEntry e = new HsEntry("87", 2, "Vehicles", Language.DE, null);
        assertThat(e.code()).isEqualTo("87");
        assertThat(e.level()).isEqualTo(2);
        assertThat(e.description()).isEqualTo("Vehicles");
        assertThat(e.language()).isEqualTo(Language.DE);
        assertThat(e.parentCode()).isNull();
    }
}
