package com.geodis.hs.matcher.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.geodis.hs.matcher.domain.Language;
import java.util.List;
import org.junit.jupiter.api.Test;

class NomenclatureRegistryBuilderTest {

    @Test
    void hierarchy8And10AreSeparateRegistryEntries() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0100000000", 2, Language.EN, "LIVE ANIMALS"),
                        new RawNomenclatureRow("0101000000", 4, Language.EN, "Live horses"),
                        new RawNomenclatureRow("0101210000", 6, Language.EN, "Horses"),
                        new RawNomenclatureRow("0101210000", 8, Language.EN, "Pure-bred line"),
                        new RawNomenclatureRow("0101210000", 10, Language.EN, "CN10 detail"));

        NomenclatureRegistry reg = NomenclatureRegistryBuilder.build(rows, Language.EN);

        assertThat(reg.get("010121"))
                .hasValueSatisfying(
                        e -> {
                            assertThat(e.level()).isEqualTo(6);
                            assertThat(e.parentCode()).isEqualTo("0101");
                            assertThat(e.description()).isEqualTo("Horses");
                        });
        assertThat(reg.get("01012100"))
                .hasValueSatisfying(
                        e -> {
                            assertThat(e.level()).isEqualTo(8);
                            assertThat(e.parentCode()).isEqualTo("010121");
                            assertThat(e.description()).isEqualTo("Pure-bred line");
                        });
        assertThat(reg.get("0101210000"))
                .hasValueSatisfying(
                        e -> {
                            assertThat(e.level()).isEqualTo(10);
                            assertThat(e.parentCode()).isEqualTo("01012100");
                            assertThat(e.description()).isEqualTo("CN10 detail");
                        });
    }

    @Test
    void euStyle_cn8WithoutExplicitHs6Line_getsSyntheticParent() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("0702000000", 4, Language.FR, "Tomates"),
                        new RawNomenclatureRow("0702001000", 8, Language.FR, "Tomates entières"));

        NomenclatureRegistry reg = NomenclatureRegistryBuilder.build(rows, Language.FR);

        assertThat(reg.get("070200")).isPresent();
        assertThat(reg.get("070200").orElseThrow().description()).isEmpty();
        assertThat(reg.get("070200").orElseThrow().parentCode()).isEqualTo("0702");
        assertThat(reg.get("07020010").orElseThrow().parentCode()).isEqualTo("070200");
    }

    @Test
    void rejectsLanguageMismatch() {
        List<RawNomenclatureRow> rows =
                List.of(new RawNomenclatureRow("0100000000", 2, Language.FR, "Animaux"));
        assertThatThrownBy(() -> NomenclatureRegistryBuilder.build(rows, Language.EN))
                .isInstanceOf(NomenclatureIngestException.class);
    }

    @Test
    void parentChainForSixDigit() {
        List<RawNomenclatureRow> rows =
                List.of(
                        new RawNomenclatureRow("8700000000", 2, Language.EN, "Ch 87"),
                        new RawNomenclatureRow("8703000000", 4, Language.EN, "Heading 8703"),
                        new RawNomenclatureRow("8703210000", 6, Language.EN, "Sub"));

        NomenclatureRegistry reg = NomenclatureRegistryBuilder.build(rows, Language.EN);

        assertThat(reg.get("870321")).isPresent();
        assertThat(reg.get("870321").orElseThrow().parentCode()).isEqualTo("8703");
        assertThat(reg.get("8703")).isPresent();
        assertThat(reg.get("8703").orElseThrow().parentCode()).isEqualTo("87");
        assertThat(reg.get("87").orElseThrow().parentCode()).isNull();
    }
}
