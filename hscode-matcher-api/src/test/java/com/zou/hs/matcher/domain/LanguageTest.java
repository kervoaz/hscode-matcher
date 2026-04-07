package com.zou.hs.matcher.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageTest {

    @Test
    void valueOf_roundTripsConstants() {
        assertThat(Language.valueOf("FR")).isSameAs(Language.FR);
        assertThat(Language.valueOf("EN")).isSameAs(Language.EN);
        assertThat(Language.valueOf("DE")).isSameAs(Language.DE);
    }

    @Test
    void enum_hasThreeMembers() {
        assertThat(Language.values()).hasSize(3);
    }
}
