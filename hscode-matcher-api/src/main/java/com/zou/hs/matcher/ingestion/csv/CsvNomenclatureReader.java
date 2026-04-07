package com.zou.hs.matcher.ingestion.csv;

import com.zou.hs.matcher.domain.Language;
import com.zou.hs.matcher.ingestion.NomenclatureIngestException;
import com.zou.hs.matcher.ingestion.RawNomenclatureRow;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public final class CsvNomenclatureReader {

    private static final CSVFormat FORMAT =
            CSVFormat.DEFAULT.builder()
                    .setHeader(
                            NomenclatureCsvFormat.GOODS_CODE,
                            NomenclatureCsvFormat.HIER_POS,
                            NomenclatureCsvFormat.LANGUAGE,
                            NomenclatureCsvFormat.DESCRIPTION)
                    .setSkipHeaderRecord(true)
                    .build();

    private CsvNomenclatureReader() {}

    public static List<RawNomenclatureRow> readAll(Path csvPath) throws IOException {
        try (Reader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            return readAll(reader);
        }
    }

    public static List<RawNomenclatureRow> readAll(Reader reader) throws IOException {
        List<RawNomenclatureRow> out = new ArrayList<>();
        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                String goods = record.get(NomenclatureCsvFormat.GOODS_CODE);
                String hier = record.get(NomenclatureCsvFormat.HIER_POS);
                String lang = record.get(NomenclatureCsvFormat.LANGUAGE);
                String desc = record.get(NomenclatureCsvFormat.DESCRIPTION);
                if (goods == null || goods.isBlank()) {
                    continue;
                }
                int hp;
                try {
                    hp = (int) Double.parseDouble(hier.trim());
                } catch (NumberFormatException e) {
                    throw new NomenclatureIngestException("Bad hier_pos in CSV: " + hier, e);
                }
                Language language;
                try {
                    language = Language.valueOf(lang.trim().toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new NomenclatureIngestException("Bad language in CSV: " + lang, e);
                }
                out.add(new RawNomenclatureRow(goods.trim(), hp, language, desc == null ? "" : desc));
            }
        }
        return out;
    }
}
