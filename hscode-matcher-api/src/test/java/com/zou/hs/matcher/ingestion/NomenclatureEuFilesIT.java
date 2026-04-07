package com.zou.hs.matcher.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.csv.CsvNomenclatureReader;
import com.zou.hs.matcher.ingestion.xlsx.XlsxNomenclatureExporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** Loads real CIRCABC exports from the repository parent directory (not shipped in JAR). */
class NomenclatureEuFilesIT {

    @Test
    void loadValidate_allThreeLanguages() throws Exception {
        for (Language lang : EnumSet.of(Language.EN, Language.FR, Language.DE)) {
            Path xlsx = Path.of("..", "Nomenclature" + lang.name() + ".XLSX");
            assumeTrue(Files.exists(xlsx), "Missing " + xlsx.toAbsolutePath());

            var rows = XlsxNomenclatureExporter.readRows(xlsx);
            NomenclatureRegistry reg = NomenclatureRegistryBuilder.build(rows, lang);
            NomenclatureIntegrityValidator.validate(reg, NomenclatureIntegrityValidator.Expectations.euCircabcFullExport());

            assertThat(reg.get("870321"))
                    .isPresent()
                    .hasValueSatisfying(
                            e -> {
                                assertThat(e.parentCode()).isEqualTo("8703");
                                assertThat(e.description()).isNotBlank();
                            });
            assertThat(reg.get("8703"))
                    .isPresent()
                    .hasValueSatisfying(e -> assertThat(e.parentCode()).isEqualTo("87"));
        }
    }

    @Test
    void roundTrip_xlsxToCsvToRegistry_en() throws Exception {
        Path xlsx = Path.of("..", "NomenclatureEN.XLSX");
        assumeTrue(Files.exists(xlsx));

        Path csv = Files.createTempFile("nomenclature-en-", ".csv");
        csv.toFile().deleteOnExit();
        XlsxNomenclatureExporter.exportToCsv(xlsx, csv);

        var rows = CsvNomenclatureReader.readAll(csv);
        NomenclatureRegistry reg = NomenclatureRegistryBuilder.build(rows, Language.EN);
        NomenclatureIntegrityValidator.validate(reg, NomenclatureIntegrityValidator.Expectations.euCircabcFullExport());
        assertThat(reg.size()).isGreaterThan(6000);
    }
}
