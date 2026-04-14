package com.geodis.hs.matcher.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoodsCodesTest {

    @Test
    void tryTenDigitCore_parsesEuFormat() {
        assertThat(GoodsCodes.tryTenDigitCore("0101210000 80")).isEqualTo("0101210000");
        assertThat(GoodsCodes.tryTenDigitCore("  0101210000 80  ")).isEqualTo("0101210000");
    }

    @Test
    void tryTenDigitCore_rejectsShortOrNotes() {
        assertThat(GoodsCodes.tryTenDigitCore("")).isNull();
        assertThat(GoodsCodes.tryTenDigitCore("12")).isNull();
        assertThat(GoodsCodes.tryTenDigitCore("Note")).isNull();
    }

    @Test
    void hsKey_byHierarchyPosition() {
        String ten = "0101210000";
        assertThat(GoodsCodes.hsKey(2, ten)).isEqualTo("01");
        assertThat(GoodsCodes.hsKey(4, ten)).isEqualTo("0101");
        assertThat(GoodsCodes.hsKey(6, ten)).isEqualTo("010121");
        assertThat(GoodsCodes.hsKey(8, ten)).isEqualTo("01012100");
        assertThat(GoodsCodes.hsKey(10, ten)).isEqualTo("0101210000");
    }

    @Test
    void levelFromKeyLength_and_parentKey_forCnLevels() {
        assertThat(GoodsCodes.levelFromKeyLength("22042986")).isEqualTo(8);
        assertThat(GoodsCodes.levelFromKeyLength("2204298610")).isEqualTo(10);
        assertThat(GoodsCodes.parentKey("22042986", 8)).isEqualTo("220429");
        assertThat(GoodsCodes.parentKey("2204298610", 10)).isEqualTo("22042986");
    }

    @Test
    void requireTenDigitCore_throws() {
        assertThatThrownBy(() -> GoodsCodes.requireTenDigitCore("bad"))
                .isInstanceOf(NomenclatureIngestException.class);
    }

    @Test
    void isValidHsKey() {
        assertThat(GoodsCodes.isValidHsKey("01")).isTrue();
        assertThat(GoodsCodes.isValidHsKey("0101")).isTrue();
        assertThat(GoodsCodes.isValidHsKey("010121")).isTrue();
        assertThat(GoodsCodes.isValidHsKey("01012100")).isTrue();
        assertThat(GoodsCodes.isValidHsKey("0101210000")).isTrue();
        assertThat(GoodsCodes.isValidHsKey("01012")).isFalse();
        assertThat(GoodsCodes.isValidHsKey("0101210")).isFalse();
        assertThat(GoodsCodes.isValidHsKey("010121000")).isFalse();
    }

    @Test
    void normalizeLookupCode_stripsNonDigits() {
        assertThat(GoodsCodes.normalizeLookupCode("01")).contains("01");
        assertThat(GoodsCodes.normalizeLookupCode("0101.21")).contains("010121");
        assertThat(GoodsCodes.normalizeLookupCode("2204.29.86.10")).contains("2204298610");
        assertThat(GoodsCodes.normalizeLookupCode("220429.86")).contains("22042986");
        assertThat(GoodsCodes.normalizeLookupCode(" 8703 ")).contains("8703");
    }

    @Test
    void normalizeLookupCode_rejectsInvalidLengths() {
        assertThat(GoodsCodes.normalizeLookupCode("")).isEmpty();
        assertThat(GoodsCodes.normalizeLookupCode("abc")).isEmpty();
        assertThat(GoodsCodes.normalizeLookupCode("1")).isEmpty();
        assertThat(GoodsCodes.normalizeLookupCode("123")).isEmpty();
        assertThat(GoodsCodes.normalizeLookupCode("12345")).isEmpty();
    }
}
